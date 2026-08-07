# cypher-guard.ps1
# PostToolUse guard for SkillAtlas.
# Enforces two CLAUDE.md rules on every .java edit:
#   1. Cypher lives ONLY in *Repository.java  (Controller/Service must be Cypher-free)
#   2. No Cypher injection: queries must use $params, never string concatenation
#
# Advisory guard: exit 2 feeds the message back to Claude as feedback on the
# just-completed edit (it does not revert the edit). Exit 0 = all clear.

$ErrorActionPreference = 'Stop'

function Read-Payload {
    try { return ([Console]::In.ReadToEnd() | ConvertFrom-Json) } catch { return $null }
}

$data = Read-Payload
if ($null -eq $data) { exit 0 }

$path = $data.tool_input.file_path
if ([string]::IsNullOrWhiteSpace($path)) { exit 0 }
if ($path -notmatch '\.java$') { exit 0 }
if (-not (Test-Path -LiteralPath $path)) { exit 0 }

$fileName = Split-Path -Leaf $path
$isRepository = $fileName -match 'Repository\.java$'
$isTest       = $path -match '[\\/]test[\\/]' -or $fileName -match 'Test'

$lines = Get-Content -LiteralPath $path
$violations = New-Object System.Collections.Generic.List[string]

# Cypher clause keywords that signal a query is present in the file.
$cypherKeyword = '\b(MATCH|MERGE|DETACH\s+DELETE|OPTIONAL\s+MATCH)\b'

for ($i = 0; $i -lt $lines.Count; $i++) {
    $line = $lines[$i]
    $n = $i + 1

    # Skip obvious single-line comments to cut false positives.
    $trimmed = $line.TrimStart()
    if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*')) { continue }

    $hasCypher = $line -match $cypherKeyword

    # --- Check 1: Cypher outside a *Repository class ---
    if ($hasCypher -and -not $isRepository -and -not $isTest) {
        $violations.Add("  L${n}: Cypher keyword outside a *Repository - move this query into the repository layer.`n        $($line.Trim())")
    }

    # --- Check 2: string-concatenated Cypher (injection risk) ---
    # A line that carries a Cypher keyword inside a string literal AND uses
    # Java string concatenation (`" +` or `+ "`) is building a query by hand.
    if ($hasCypher -and ($line -match '"\s*\+' -or $line -match '\+\s*"')) {
        $violations.Add("  L${n}: Cypher built via string concatenation - use `$param binding instead.`n        $($line.Trim())")
    }
    # String.format used to assemble a query is the same problem.
    if ($hasCypher -and $line -match 'String\.format\s*\(') {
        $violations.Add("  L${n}: Cypher assembled with String.format - use `$param binding instead.`n        $($line.Trim())")
    }
}

if ($violations.Count -gt 0) {
    $msg = "SkillAtlas Cypher guard blocked $fileName :`n" + ($violations -join "`n") + "`n`nSee CLAUDE.md: Cypher only in *Repository; always parameterize (`$param), never string-concat."
    [Console]::Error.WriteLine($msg)
    exit 2
}

exit 0
