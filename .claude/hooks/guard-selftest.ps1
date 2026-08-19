# guard-selftest.ps1 - asserts guard-lib.ps1 against real lines in this repo.
#
# Run from the repo root:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .claude\hooks\guard-selftest.ps1
#
# Every case here is a line that exists in the codebase today, including the two
# false positives the old cypher-guard.ps1 produced. If this goes red, a wave-2
# rule built on top of the library would be reporting nonsense.

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\guard-lib.ps1"

$script:Fail = 0

function Assert-That {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        Write-Host "  ok   $Name"
    } else {
        Write-Host "  FAIL $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "       $Detail" -ForegroundColor Red }
        $script:Fail++
    }
}

function Get-Analysis {
    param([string]$RelPath)
    $full = Join-Path (Get-Location) $RelPath
    $lines = @(Get-Content -LiteralPath $full)
    $ext = [System.IO.Path]::GetExtension($full).ToLowerInvariant()
    return @{
        Lines    = $lines
        Analysis = (Get-LineAnalysis -Lines $lines -TextBlocks:($ext -eq '.java'))
    }
}

Write-Host ''
Write-Host 'literal detection'

# The old guard's false positive #1: Map.merge is lowercase and outside quotes.
$fs = Get-Analysis 'src\main\java\com\skillatlas\finder\FinderService.java'
$mergeLines = @(0..($fs.Lines.Count - 1) | Where-Object { $fs.Lines[$_] -match '\.merge\(' })
Assert-That 'FinderService has a Map.merge call' ($mergeLines.Count -gt 0)
foreach ($i in $mergeLines) {
    Assert-That "FinderService L$($i + 1) is not Cypher" `
        (-not (Test-Cypher $fs.Analysis[$i].Literal)) $fs.Lines[$i].Trim()
}

# Java text blocks: the query lines inside """ ... """ carry no quotes at all.
$dash = Get-Analysis 'src\main\java\com\skillatlas\dashboard\DashboardRepository.java'
$blockHits = @(0..($dash.Lines.Count - 1) | Where-Object {
    $dash.Analysis[$_].Literal -and (Test-Cypher $dash.Analysis[$_].Literal)
})
Assert-That 'text-block Cypher is seen (DashboardRepository)' ($blockHits.Count -gt 0) `
    "matched $($blockHits.Count) lines"

# Prose and comments must not read as Cypher.
$fake = Get-LineAnalysis -Lines @('// return the current user with skills', '* MERGE is idempotent') -TextBlocks
Assert-That 'comment mentioning MERGE is not Cypher' (-not (Test-Cypher $fake[1].Literal))
Assert-That 'prose with return/with is not Cypher'   (-not (Test-Cypher $fake[0].Literal))

Write-Host ''
Write-Host 'skeleton (literal masking)'

# The old guard's false positive #2: a parameterized fixture wrapped over two
# lines. The window must show literal + literal, never literal + variable.
$git = Get-Analysis 'src\test\java\com\skillatlas\graph\GraphIT.java'
$starts = @(0..($git.Lines.Count - 1) | Where-Object { $git.Lines[$_] -match 'run\("MATCH' })
Assert-That 'GraphIT has wrapped fixture queries' ($starts.Count -gt 0)

$windows = @($starts | ForEach-Object { Get-SkeletonWindow -Analysis $git.Analysis -Index $_ })
$joined  = @($windows | Where-Object { $_ -match '@S@\s*\+\s*@S@' })
Assert-That 'wrapped fixture reads as literal + literal in a 3-line window' `
    ($joined.Count -gt 0) "matched $($joined.Count) of $($windows.Count) windows"

$dirty = @($windows | Where-Object { $_ -match '@S@\s*\+\s*[A-Za-z_$(]' })
Assert-That 'no GraphIT window looks like literal + variable' ($dirty.Count -eq 0) `
    ($dirty -join ' | ')

$inj = Get-LineAnalysis -Lines @('String q = "MATCH (p:Person) WHERE p.name = ''" + name + "''";') -TextBlocks
Assert-That 'injection shape leaves a bare identifier in the skeleton' `
    ($inj[0].Skeleton -match '@S@\s*\+\s*[A-Za-z_]') $inj[0].Skeleton

Write-Host ''
Write-Host 'touched-lines filter'

$long = 'color: #e2777a; /* pre-existing */'
Assert-That 'untouched long line is filtered'  (-not (Test-Touched $long 'padding: 16px;'))
Assert-That 'touched long line survives'       (Test-Touched $long "x`n$long`ny")
Assert-That 'short line is never filtered'     (Test-Touched '100' 'anything')
Assert-That 'empty edit filters everything'    (-not (Test-Touched $long ''))

Write-Host ''
Write-Host 'guard:allow markers'

$marked = @(
    'boolean existsByEmail(String email);',
    '// guard:allow soft-delete - seeding uniqueness probe',
    'boolean existsByEmail(String email);'
)
Assert-That 'marker on the line above is honoured'  (Test-AllowMarker -Lines $marked -Index 2 -Rule 'soft-delete')
Assert-That 'marker does not leak to another rule'  (-not (Test-AllowMarker -Lines $marked -Index 2 -Rule 'cypher-concat'))
Assert-That 'unmarked line is not suppressed'       (-not (Test-AllowMarker -Lines @('int x = 1;') -Index 0 -Rule 'soft-delete'))

$fileScoped = @('package com.skillatlas.common;', '// guard:allow cypher-location - startup DDL') + (1..40 | ForEach-Object { "line $_" })
Assert-That 'file-scoped marker covers a late line' (Test-AllowMarker -Lines $fileScoped -Index 30 -Rule 'cypher-location')

Write-Host ''
if ($script:Fail -gt 0) {
    Write-Host "$script:Fail assertion(s) failed" -ForegroundColor Red
    exit 1
}
Write-Host 'all green' -ForegroundColor Green
exit 0
