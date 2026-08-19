# guard-lib.ps1 - shared machinery for the SkillAtlas guard.
#
# Nothing here knows any project rule. It provides the three anti-noise
# mechanisms every rule gets for free:
#   1. touched-lines - a violation is reported only if this edit touched the line
#   2. literal/skeleton analysis - tells code apart from string content
#   3. guard:allow markers - deliberate exceptions recorded next to the code
#
# ASCII only: the hook runs under Windows PowerShell 5.1, which mangles
# BOM-less UTF-8 source.

# ---------------------------------------------------------------- payload ---

function Get-HookPayload {
    try { return ([Console]::In.ReadToEnd() | ConvertFrom-Json) } catch { return $null }
}

# The text this edit actually introduced. Everything else in the file is
# pre-existing and must not be reported.
function Get-TouchedContent {
    param($Payload)
    if ($null -eq $Payload) { return '' }
    $in = $Payload.tool_input
    if ($null -eq $in) { return '' }

    $names = $in.PSObject.Properties.Name
    $parts = New-Object System.Collections.Generic.List[string]

    if ($names -contains 'new_string' -and $in.new_string) { $parts.Add([string]$in.new_string) }
    if ($names -contains 'content'    -and $in.content)    { $parts.Add([string]$in.content) }
    if ($names -contains 'edits'      -and $in.edits) {
        foreach ($e in $in.edits) {
            if ($e.PSObject.Properties.Name -contains 'new_string' -and $e.new_string) {
                $parts.Add([string]$e.new_string)
            }
        }
    }
    return ($parts -join "`n")
}

# ------------------------------------------------------------ line analysis --

$script:LiteralRegex = '"(?:[^"\\]|\\.)*"'

# Splits every line into the string content it carries and the code around it.
# Java text blocks matter here: inside a """ ... """ block the query lines carry
# no quotes at all, so a naive per-line regex would go blind on 9 repository
# files (DashboardRepository, FinderRepository, GraphRepository, ...).
function Get-LineAnalysis {
    param([string[]]$Lines, [switch]$TextBlocks)

    $out = New-Object System.Collections.Generic.List[psobject]
    $inTextBlock = $false

    foreach ($raw in $Lines) {
        $line = [string]$raw
        $literal = ''
        $code = $line

        if ($TextBlocks) {
            $idx = $line.IndexOf('"""')
            if ($inTextBlock) {
                if ($idx -ge 0) {
                    $literal = $line.Substring(0, $idx)
                    $code = $line.Substring($idx + 3)
                    $inTextBlock = $false
                } else {
                    $literal = $line
                    $code = ''
                }
            } elseif ($idx -ge 0) {
                $code = $line.Substring(0, $idx)
                $literal = $line.Substring($idx + 3)
                $inTextBlock = $true
            }
        }

        $trimmed = $code.TrimStart()
        $isComment = $trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')

        $skeleton = ''
        if (-not $isComment -and $code) {
            foreach ($m in [regex]::Matches($code, $script:LiteralRegex)) {
                $literal = $literal + ' ' + $m.Value.Substring(1, $m.Value.Length - 2)
            }
            $skeleton = [regex]::Replace($code, $script:LiteralRegex, '@S@')
        }

        $out.Add([pscustomobject]@{
            Text      = $line
            Literal   = $literal
            Skeleton  = $skeleton
            IsComment = $isComment
        })
    }
    return $out
}

# -------------------------------------------------------------- detection ---

# Strong Cypher clauses. Deliberately short: RETURN / WITH / SET are ordinary
# English words and would fire on prose and on comments.
$script:CypherStrong = '(MATCH\s|MERGE\s|OPTIONAL\s+MATCH|DETACH\s+DELETE|UNWIND\s|CREATE\s+CONSTRAINT|CREATE\s+INDEX|DROP\s+CONSTRAINT)'

# Graph shape: (p:Person), -[:KNOWS]->, <-[: . Catches continuation lines that
# carry no keyword at all, and stays case-independent.
$script:CypherShape = '(\(\w*:\w+|\)-\[:|\]->\(|<-\[:|\]-\()'

# Case-sensitive on the keyword half on purpose: Java's Map.merge(...) is
# lowercase, while Cypher in this repo is uppercase in every @Query and every
# query constant. That single -cmatch is what stops FinderService.java:74 from
# being reported as a stray query.
function Test-Cypher {
    param([string]$LiteralText)
    if (-not $LiteralText) { return $false }
    if ($LiteralText -cmatch $script:CypherStrong) { return $true }
    if ($LiteralText -match $script:CypherShape) { return $true }
    return $false
}

# ----------------------------------------------------------------- window ---

# Concatenated Cypher is routinely wrapped across lines, and the middle line
# carries no literal at all:
#     run("MATCH (p:Person {id: $a}) "
#             + "MERGE (p)-[:WORKED_ON]->(pr)",
#             Map.of(...));
# A per-line scan sees "@S@" then "+ @S@" and concludes nothing. Rules that
# judge concatenation must look at a window, not a line.
function Get-SkeletonWindow {
    param($Analysis, [int]$Index, [int]$Size = 3)
    $end = [Math]::Min($Analysis.Count - 1, $Index + $Size - 1)
    $parts = @()
    for ($i = $Index; $i -le $end; $i++) { $parts += $Analysis[$i].Skeleton }
    return ($parts -join ' ')
}

function Get-LiteralWindow {
    param($Analysis, [int]$Index, [int]$Size = 3)
    $end = [Math]::Min($Analysis.Count - 1, $Index + $Size - 1)
    $parts = @()
    for ($i = $Index; $i -le $end; $i++) { $parts += $Analysis[$i].Literal }
    return ($parts -join ' ')
}

# ---------------------------------------------------------------- markers ---

# // guard:allow <rule-id> - reason
# Honoured on the line itself, the line above, or anywhere in the first 15 lines
# (file-scoped). Same syntax inside <!-- --> for html/css/ts.
function Test-AllowMarker {
    param([string[]]$Lines, [int]$Index, [string]$Rule)

    $pattern = 'guard:allow\s+' + [regex]::Escape($Rule) + '\b'

    if ($Index -ge 0 -and $Index -lt $Lines.Count -and $Lines[$Index] -match $pattern) { return $true }
    if ($Index -ge 1 -and $Lines[$Index - 1] -match $pattern) { return $true }

    $head = [Math]::Min(15, $Lines.Count)
    for ($i = 0; $i -lt $head; $i++) {
        if ($Lines[$i] -match $pattern) { return $true }
    }
    return $false
}

# ----------------------------------------------------------- touched lines --

# A short line ("100") would match half the file by containment, so anything
# under 8 characters is reported rather than filtered - a tiny edit produces
# few violations anyway.
function Test-Touched {
    param([string]$LineText, [string]$TouchedContent)
    if (-not $TouchedContent) { return $false }
    $t = $LineText.Trim()
    if ($t.Length -lt 8) { return $true }
    return $TouchedContent.Contains($t)
}

# ---------------------------------------------------------------- context ---

function New-GuardContext {
    param([string]$Path, [string[]]$Lines, [string]$TouchedContent, [switch]$All)

    $ext = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    return [pscustomobject]@{
        Path       = $Path
        FileName   = Split-Path -Leaf $Path
        Ext        = $ext
        Lines      = $Lines
        Analysis   = (Get-LineAnalysis -Lines $Lines -TextBlocks:($ext -eq '.java'))
        Touched    = $TouchedContent
        All        = [bool]$All
        Violations = (New-Object System.Collections.Generic.List[psobject])
    }
}

# The single choke point every rule goes through. A rule cannot forget the
# touched-lines filter or the allow marker, because neither is its job.
function Add-Violation {
    param(
        $Ctx,
        [string]$Rule,
        [int]$Line,                 # 1-based; 0 = file-scoped finding
        [ValidateSet('SECURITY', 'CONVENTION')] [string]$Severity,
        [string]$Message
    )

    $idx = $Line - 1

    if (Test-AllowMarker -Lines $Ctx.Lines -Index $idx -Rule $Rule) { return }

    if (-not $Ctx.All -and $Line -gt 0) {
        if (-not (Test-Touched -LineText $Ctx.Lines[$idx] -TouchedContent $Ctx.Touched)) { return }
    }

    $snippet = ''
    if ($idx -ge 0 -and $idx -lt $Ctx.Lines.Count) { $snippet = $Ctx.Lines[$idx].Trim() }

    $Ctx.Violations.Add([pscustomobject]@{
        Rule     = $Rule
        Line     = $Line
        Severity = $Severity
        Message  = $Message
        Snippet  = $snippet
    })
}

# ----------------------------------------------------------------- report ---

function Format-GuardReport {
    param($Ctx)

    $v = $Ctx.Violations
    if ($v.Count -eq 0) { return '' }

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("SkillAtlas guard - $($Ctx.Path)")

    $shown = 0
    foreach ($sev in @('SECURITY', 'CONVENTION')) {
        $group = @($v | Where-Object { $_.Severity -eq $sev })
        if ($group.Count -eq 0) { continue }
        [void]$sb.AppendLine('')
        [void]$sb.AppendLine("  $sev")
        foreach ($item in $group) {
            if ($shown -ge 10) { continue }
            $where = '--'
            if ($item.Line -gt 0) { $where = "L$($item.Line)" }
            [void]$sb.AppendLine("  [$($item.Rule)] $where  $($item.Message)")
            if ($item.Snippet) { [void]$sb.AppendLine("        $($item.Snippet)") }
            $shown++
        }
    }

    if ($v.Count -gt $shown) {
        [void]$sb.AppendLine('')
        [void]$sb.AppendLine("  ... and $($v.Count - $shown) more")
    }

    [void]$sb.AppendLine('')
    [void]$sb.AppendLine("  Deliberate exception:  // guard:allow $($v[0].Rule) - <reason>")
    return $sb.ToString()
}
