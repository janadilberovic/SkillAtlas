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
$joined  = @($windows | Where-Object { $_ -match '@Q@\s*\+\s*@Q@' })
Assert-That 'wrapped fixture reads as query + query in a 3-line window' `
    ($joined.Count -gt 0) "matched $($joined.Count) of $($windows.Count) windows"

$dirty = @($windows | Where-Object { $_ -match '@Q@\s*\+\s*[A-Za-z_$(]' })
Assert-That 'no GraphIT window looks like query + variable' ($dirty.Count -eq 0) `
    ($dirty -join ' | ')

# A query literal and an ordinary one must not be the same token: the injection
# regression tests put the Cypher payload one line above "Bearer " + token.
$mixed = Get-LineAnalysis -Lines @('.param("skills", "React''}) DETACH DELETE (n) //")', '.header("Authorization", "Bearer " + token))') -TextBlocks
Assert-That 'payload literal is tagged @Q@'      ($mixed[0].Skeleton -match '@Q@')
Assert-That 'Bearer header literal is tagged @S@' ($mixed[1].Skeleton -match '@S@\s*\+\s*token') $mixed[1].Skeleton

$inj = Get-LineAnalysis -Lines @('String q = "MATCH (p:Person) WHERE p.name = ''" + name + "''";') -TextBlocks
Assert-That 'injection shape leaves a bare identifier next to the query token' `
    ($inj[0].Skeleton -match '@Q@\s*\+\s*[A-Za-z_]') $inj[0].Skeleton

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
Write-Host 'java rules - do they fire when they should?'

. "$PSScriptRoot\rules-java.ps1"

# Runs a rule set over synthetic source and returns the rule ids it reported.
function Get-Rules {
    param([string[]]$Source, [string]$Path = 'C:\repo\src\main\java\com\skillatlas\x\XService.java')
    $ctx = New-GuardContext -Path $Path -Lines $Source -TouchedContent '' -All
    Invoke-JavaRules -Ctx $ctx
    return @($ctx.Violations | ForEach-Object { $_.Rule })
}

$hits = Get-Rules @('var r = client.query("MATCH (p:Person) RETURN p").fetch();')
Assert-That 'cypher-location fires on a query in a service' ($hits -contains 'cypher-location') ($hits -join ',')

$hits = Get-Rules @('var r = client.query("MATCH (p:Person) RETURN p").fetch();') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XRepository.java'
Assert-That 'cypher-location stays quiet in a repository' ($hits -notcontains 'cypher-location') ($hits -join ',')

$hits = Get-Rules @('String q = "MATCH (p:Person) WHERE p.name = ''" + name + "''";') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XRepository.java'
Assert-That 'cypher-concat fires on query + variable' ($hits -contains 'cypher-concat') ($hits -join ',')

$hits = Get-Rules @('String q = String.format("MATCH (p:Person) WHERE p.name = ''%s''", name);') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XRepository.java'
Assert-That 'cypher-concat fires on String.format' ($hits -contains 'cypher-concat') ($hits -join ',')

$hits = Get-Rules @('mvc.perform(get("/api/v1/experts")', '.param("skills", "React''}) DETACH DELETE (n) //")', '.header("Authorization", "Bearer " + token))') `
    -Path 'C:\repo\src\test\java\com\skillatlas\x\XIT.java'
Assert-That 'injection regression test stays quiet' ($hits.Count -eq 0) ($hits -join ',')

$hits = Get-Rules @('private static final String Q = SKILL_GAP_MATCH + """', '    MATCH (p:Person) RETURN p', '    """;') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XRepository.java'
Assert-That 'constant + text block is not read as concatenation' ($hits -notcontains 'cypher-concat') ($hits -join ',')
Assert-That 'unfiltered Person read in that block is caught' ($hits -contains 'soft-delete') ($hits -join ',')

$hits = Get-Rules @('private static final String Q = """', '    MATCH (p:Person) WHERE p.name = ''', '    """ + name;') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XRepository.java'
Assert-That 'text block + variable fires' ($hits -contains 'cypher-concat') ($hits -join ',')

$repo = (Get-Location).Path
$exc = "$repo\src\main\java\com\skillatlas\x\exception\NeverRegisteredException.java"
$hits = Get-Rules @('public class NeverRegisteredException extends RuntimeException { }') -Path $exc
Assert-That 'exception-unregistered fires on an unhandled exception' ($hits -contains 'exception-unregistered') ($hits -join ',')

$known = "$repo\src\main\java\com\skillatlas\people\exception\PersonNotFoundException.java"
$hits = Get-Rules @('public class PersonNotFoundException extends RuntimeException { }') -Path $known
Assert-That 'exception-unregistered stays quiet on a registered one' ($hits -notcontains 'exception-unregistered') ($hits -join ',')

$hits = Get-Rules @('public PageResponse<XResponse> list(@RequestParam(defaultValue = "20") int size) {') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XController.java'
Assert-That 'page-size-uncapped fires without a cap' ($hits -contains 'page-size-uncapped') ($hits -join ',')

$hits = Get-Rules @('private static final int MAX_PAGE_SIZE = 100;', 'public PageResponse<XResponse> list(@RequestParam(defaultValue = "20") int size) {') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XController.java'
Assert-That 'page-size-uncapped stays quiet with a cap' ($hits -notcontains 'page-size-uncapped') ($hits -join ',')

$hits = Get-Rules @('public Person get(@PathVariable String id) {') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XController.java'
Assert-That 'entity-returned fires on a bare entity' ($hits -contains 'entity-returned') ($hits -join ',')

$hits = Get-Rules @('public PersonProfileResponse get(@PathVariable String id) {', 'public PageResponse<PersonResponse> list() {', 'public XController(XService s) {') `
    -Path 'C:\repo\src\main\java\com\skillatlas\x\XController.java'
Assert-That 'entity-returned ignores DTOs and constructors' ($hits -notcontains 'entity-returned') ($hits -join ',')

Write-Host ''
if ($script:Fail -gt 0) {
    Write-Host "$script:Fail assertion(s) failed" -ForegroundColor Red
    exit 1
}
Write-Host 'all green' -ForegroundColor Green
exit 0
