# rules-java.ps1 - rules that run on .java files.
#
# A rule reports through Add-Violation and never filters for itself: the
# touched-lines check and the guard:allow marker are handled in guard-lib.ps1.
#
# Still to land:
#   soft-delete             Person read without an isDeleted filter  (LAST, gated
#                           on a clean dry run)

# @Node entities. Returning one from a controller serialises passwordHash,
# isDeleted and the whole relationship graph hanging off it.
$script:EntityNames = '\b(Person|Skill|Project|Team|Knows|Mentors|WorkedOn|WantsToLearn)\b'

function Get-MainSourceRoot {
    param([string]$Path)
    $norm = $Path -replace '/', '\'
    $idx = $norm.IndexOf('\src\main\java\')
    if ($idx -lt 0) { return $null }
    return $norm.Substring(0, $idx + '\src\main\java\'.Length)
}

# A '+' next to a query literal is only dangerous when the other operand is not
# a literal. Two shapes are safe and both occur in this repo:
#   run("MATCH ... "  +  "MERGE ...")        wrapped across lines
#   SKILL_GAP_MATCH   +  """ ... """         a query assembled from constants
# The second is why an ALL_CAPS operand is treated as a constant: every query
# fragment in DashboardRepository / FinderRepository / MentoringRepository is a
# private static final String, while user input arrives as a lowerCamelCase
# parameter.
function Test-UnsafeConcat {
    param([string]$Skeleton)

    foreach ($pattern in @('@Q@\s*\+\s*([A-Za-z_$][\w$]*)', '([A-Za-z_$][\w$]*)\s*\+\s*@Q@')) {
        foreach ($m in [regex]::Matches($Skeleton, $pattern)) {
            $name = $m.Groups[1].Value
            if ($name -cne $name.ToUpperInvariant()) { return $true }
        }
    }
    if ($Skeleton -match '(String\.format|\.formatted)\s*\(') { return $true }
    return $false
}

function Invoke-JavaRules {
    param($Ctx)

    $isRepository = $Ctx.FileName -match 'Repository\.java$'
    $isTest = $Ctx.Path -match '[\\/]test[\\/]'
    if (-not $isRepository) {
        # The five Neo4jClient-based repositories are @Repository classes rather
        # than Neo4jRepository interfaces. Checking the annotation keeps the rule
        # working if one is ever named something other than *Repository.
        foreach ($l in $Ctx.Lines) { if ($l -match '^\s*@Repository\b') { $isRepository = $true; break } }
    }

    $a = $Ctx.Analysis
    $prevWasCypher = $false

    for ($i = 0; $i -lt $a.Count; $i++) {
        $isCypher = Test-Cypher $a[$i].Literal

        # --- cypher-location ---------------------------------------------
        # Reported once per query, not once per line: a text block spanning ten
        # lines is one mistake, not ten.
        if ($isCypher -and -not $prevWasCypher -and -not $isRepository -and -not $isTest) {
            Add-Violation -Ctx $Ctx -Rule 'cypher-location' -Line ($i + 1) -Severity 'SECURITY' `
                -Message 'Cypher outside a *Repository - move the query into the repository layer (CLAUDE.md, Architecture).'
        }

        # --- cypher-concat -----------------------------------------------
        # The window looks forward 3 lines, because a wrapped query has no
        # literal at all on its middle line. But the anchor line must carry the
        # query itself: otherwise an unrelated string concatenation upstream of
        # an injection-test payload reads as a finding. The regression tests do
        # exactly that - a URL built with + on one line, the payload
        # "React'}) DETACH DELETE (n) //" on the next.
        if ($isCypher) {
            if (Test-UnsafeConcat (Get-SkeletonWindow -Analysis $a -Index $i)) {
                Add-Violation -Ctx $Ctx -Rule 'cypher-concat' -Line ($i + 1) -Severity 'SECURITY' `
                    -Message 'Cypher assembled from a non-literal - bind it as $param instead (spec 5: Cypher injection).'
            }
        }

        $prevWasCypher = $isCypher
    }

    # --- exception-unregistered ------------------------------------------
    # GlobalExceptionHandler has no generic Exception fallback, so an
    # unregistered domain exception is a 500 with a Whitelabel body - and no
    # test catches it unless an IT happens to walk that path.
    # The capturing match must be last: a second -match in the same condition
    # overwrites $Matches, and the group would come back null.
    if ($Ctx.Path -match '[\\/]main[\\/]' -and $Ctx.Path -match '[\\/]exception[\\/](\w+Exception)\.java$') {
        $name = $Matches[1]
        $root = Get-MainSourceRoot -Path $Ctx.Path
        if ($root) {
            $handler = Join-Path $root 'com\skillatlas\common\GlobalExceptionHandler.java'
            if ((Test-Path -LiteralPath $handler) -and -not (Select-String -LiteralPath $handler -SimpleMatch $name -Quiet)) {
                $at = 0
                for ($i = 0; $i -lt $Ctx.Lines.Count; $i++) {
                    if ($Ctx.Lines[$i] -match "class\s+$name\b") { $at = $i + 1; break }
                }
                Add-Violation -Ctx $Ctx -Rule 'exception-unregistered' -Line $at -Severity 'CONVENTION' `
                    -Message "$name has no @ExceptionHandler in GlobalExceptionHandler - there is no generic fallback, so it surfaces as a 500."
            }
        }
    }

    # --- soft-delete ------------------------------------------------------
    # Person is the only soft-deletable node (@Property("isDeleted")), and spec
    # 4.6 requires the filter on every read. DevSeeder plants two deleted people
    # precisely so a missing filter shows up as wrong data rather than nothing.
    #
    # Judged per query region - a run of consecutive literal-carrying lines - so
    # a text block is one verdict, not one per line.
    if ($isRepository) {
        $i = 0
        while ($i -lt $a.Count) {
            if (-not $a[$i].Literal.Trim()) { $i++; continue }

            $start = $i
            $parts = New-Object System.Collections.Generic.List[string]
            while ($i -lt $a.Count -and $a[$i].Literal.Trim()) {
                $parts.Add($a[$i].Literal)
                $i++
            }
            $region = $parts -join ' '

            # A read always has a MATCH. Projection fragments such as
            # MentoringRepository's WALK_PROJECTION mention n:Person inside a
            # CASE but read nothing - the filter belongs to the query that
            # embeds them.
            $touchesPerson = ($region -match ':Person\b') -and ($region -cmatch 'MATCH\s')
            $hasFilter = $region -match 'isDeleted|DeletedFalse'
            # A delete path is legitimately unfiltered: removing a relationship
            # from an already-deleted person is harmless.
            $isDelete = $region -cmatch '\bDELETE\b'

            if ($touchesPerson -and -not $hasFilter -and -not $isDelete) {
                Add-Violation -Ctx $Ctx -Rule 'soft-delete' -Line ($start + 1) -Severity 'SECURITY' `
                    -Message 'Person read without a soft-delete filter - add WHERE p.isDeleted = false (spec 4.6).'
            }
        }

        # Derived query methods carry the filter in their name instead
        # (findByIdAndDeletedFalse). Methods annotated with @Query are judged by
        # the region check above, not by their name.
        if ($Ctx.FileName -match '^People.*Repository\.java$') {
            for ($i = 0; $i -lt $Ctx.Lines.Count; $i++) {
                $line = $Ctx.Lines[$i]
                # Interface declarations only. A method with a body in a
                # @Repository class delegates to a query constant, which the
                # region check above has already judged.
                if ($line -notmatch ';\s*$') { continue }
                if ($line -notmatch '^\s*[\w<>,\s\[\]\.]+\s+(find|exists|count|get)[A-Za-z]*\s*\(') { continue }
                if ($line -match 'Deleted') { continue }

                $annotated = $false
                for ($k = [Math]::Max(0, $i - 3); $k -lt $i; $k++) {
                    if ($Ctx.Lines[$k] -match '@Query') { $annotated = $true; break }
                }
                if ($annotated) { continue }

                Add-Violation -Ctx $Ctx -Rule 'soft-delete' -Line ($i + 1) -Severity 'SECURITY' `
                    -Message 'Derived query on Person with no soft-delete filter - name it ...AndDeletedFalse (spec 4.6).'
            }
        }
    }

    if ($Ctx.FileName -match 'Controller\.java$') {

        # --- page-size-uncapped ------------------------------------------
        $hasCap = $false
        foreach ($l in $Ctx.Lines) { if ($l -match 'MAX_PAGE_SIZE') { $hasCap = $true; break } }
        if (-not $hasCap) {
            for ($i = 0; $i -lt $Ctx.Lines.Count; $i++) {
                if ($Ctx.Lines[$i] -match '@RequestParam[^)]*\bsize\b' -or $Ctx.Lines[$i] -match '\bint\s+size\b') {
                    Add-Violation -Ctx $Ctx -Rule 'page-size-uncapped' -Line ($i + 1) -Severity 'CONVENTION' `
                        -Message 'Paginated endpoint with no MAX_PAGE_SIZE cap - ?size=1000000 would pull the whole table. Mirror PeopleController, or promote the constant to com.skillatlas.common.'
                    break
                }
            }
        }

        # --- entity-returned ---------------------------------------------
        # \b keeps PersonResponse and PersonProfileResponse out of it, which is
        # why this needs no exclusion list. Services may return entities; the
        # controller maps them with XResponse::from.
        for ($i = 0; $i -lt $Ctx.Lines.Count; $i++) {
            if ($Ctx.Lines[$i] -match '^\s*(public|protected)\s+(?<ret>[\w<>,\s\[\]\.]+?)\s+\w+\s*\(') {
                if ($Matches['ret'] -cmatch $script:EntityNames) {
                    Add-Violation -Ctx $Ctx -Rule 'entity-returned' -Line ($i + 1) -Severity 'SECURITY' `
                        -Message 'Controller returns an entity - map it to a response DTO (XResponse::from). The entity carries passwordHash, isDeleted and the relationship graph.'
                }
            }
        }
    }
}
