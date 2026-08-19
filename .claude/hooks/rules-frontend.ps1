# rules-frontend.ps1 - rules that run on files under frontend/src/.
#
# A rule reports through Add-Violation and never filters for itself: the
# touched-lines check and the guard:allow marker are handled in guard-lib.ps1.
#
# hardcoded-color deliberately skips .ts and .html. Skill.color is a real API
# field (SkillInput.color in api.ts), so the hexes in mock-data.ts are domain
# data, and the ones in templates are decorative inline SVG. Turning the rule on
# there would catch one real violation (the PALETTE const in
# skills-catalog.component.ts) at the cost of a dozen false ones - that lesson
# belongs in the new-screen skill, where it costs nothing.

$script:NocturneHint = 'use a var(--...) token: --surface, --surface-2, --border, --border-strong, --text-2, --text-dim, --accent, --node-*'

function Get-FrontendAppRoot {
    param([string]$Path)
    $norm = $Path -replace '/', '\'
    $idx = $norm.IndexOf('\frontend\src\app\')
    if ($idx -lt 0) { return $null }
    return $norm.Substring(0, $idx + '\frontend\src\app\'.Length)
}

# True when app.config.ts binds $name in the way $keyword requires. Missing file
# means "cannot tell" - never report on a guess.
function Test-BoundInConfig {
    param([string]$Path, [string]$Keyword, [string]$Name)
    $root = Get-FrontendAppRoot -Path $Path
    if (-not $root) { return $true }
    $config = Join-Path $root 'app.config.ts'
    if (-not (Test-Path -LiteralPath $config)) { return $true }
    return [bool](Select-String -LiteralPath $config -Pattern "$Keyword\s*:\s*$Name\b" -Quiet)
}

function Invoke-FrontendRules {
    param($Ctx)

    $lines = $Ctx.Lines
    $path = $Ctx.Path -replace '/', '\'
    $ext = $Ctx.Ext

    # ---------------------------------------------------------------- .html --
    if ($ext -eq '.html') {
        $inSelectComponent = $path -match '\\shared\\components\\select\\'

        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]

            if (-not $inSelectComponent -and $line -match '<select\b') {
                Add-Violation -Ctx $Ctx -Rule 'native-select' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'Native <select> cannot be themed - use <sa-select [options]="..." [value]="..." (valueChange)="..." />.'
            }

            if ($line -match '\*ngIf|\*ngFor|\*ngSwitch|\[ngClass\]') {
                Add-Violation -Ctx $Ctx -Rule 'legacy-control-flow' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'The codebase is 100% @if / @for / @empty - there is no *ngIf or CommonModule anywhere.'
            }
        }
    }

    # ------------------------------------------------------------ .css/.scss --
    if ($ext -eq '.css' -or $ext -eq '.scss') {
        if ($Ctx.FileName -ne 'styles.scss') {
            for ($i = 0; $i -lt $lines.Count; $i++) {
                if ($lines[$i] -match '#[0-9a-fA-F]{3,8}\b') {
                    Add-Violation -Ctx $Ctx -Rule 'hardcoded-color' -Line ($i + 1) -Severity 'CONVENTION' `
                        -Message "Hardcoded colour - every value lives on :root in styles.scss; $script:NocturneHint."
                }
            }
        }
        return
    }

    if ($ext -ne '.ts') { return }

    # ------------------------------------------------------------------ .ts --
    $isSeam = $path -match '\\core\\api\\'
    $isAuth = $path -match '\\core\\auth\\'
    $isConfig = $Ctx.FileName -eq 'app.config.ts'

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]

        # --- http-outside-seam ------------------------------------------
        if (-not $isSeam -and -not $isAuth -and -not $isConfig) {
            if ($line -match '\bHttpClient\b' -or $line -match "from '@angular/common/http'") {
                Add-Violation -Ctx $Ctx -Rule 'http-outside-seam' -Line ($i + 1) -Severity 'SECURITY' `
                    -Message 'Components inject the abstract token from core/api/api.ts. The real call belongs in core/api/http-api.ts plus one { provide: XApi, useClass: HttpXApi } line in app.config.ts.'
            }
        }

        # --- constructor-di ---------------------------------------------
        # A bare constructor() {} is the house pattern - people-list starts its
        # loading there - so only a parameter list is reported.
        if ($line -match 'constructor\s*\(\s*(private|public|protected|readonly|@)') {
            Add-Violation -Ctx $Ctx -Rule 'constructor-di' -Line ($i + 1) -Severity 'CONVENTION' `
                -Message 'Dependencies come from inject() in a private readonly field, not from constructor parameters.'
        }

        # --- legacy-control-flow ----------------------------------------
        if ($line -match '\bCommonModule\b|\bNgIf\b|\bNgFor\b|\bNgSwitch\b') {
            Add-Violation -Ctx $Ctx -Rule 'legacy-control-flow' -Line ($i + 1) -Severity 'CONVENTION' `
                -Message 'CommonModule is not imported anywhere in this codebase - control flow is @if / @for / @empty.'
        }

        # --- mock-api-bound ---------------------------------------------
        if ($isConfig -and $line -match 'useClass\s*:\s*Mock') {
            Add-Violation -Ctx $Ctx -Rule 'mock-api-bound' -Line ($i + 1) -Severity 'CONVENTION' `
                -Message 'mock-api.ts stays unbound. A screen either shows real data or routes to WaitingForApiComponent.'
        }

        # --- route-not-lazy ---------------------------------------------
        if ($Ctx.FileName -eq 'app.routes.ts') {
            # Case-sensitive with a word boundary, so loadComponent: is not a hit
            # and a route written on one line still is.
            if ($line -cmatch '\bcomponent\s*:') {
                Add-Violation -Ctx $Ctx -Rule 'route-not-lazy' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'Every route is lazy: loadComponent: () => import(...).then((m) => m.X).'
            }
            if ($line -match "^import\s.*from\s+'\./features/") {
                Add-Violation -Ctx $Ctx -Rule 'route-not-lazy' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'An eager feature import defeats the lazy chunk even when loadComponent is used.'
            }
        }

        # --- api-token-unbound ------------------------------------------
        # Forgetting the app.config.ts line compiles and builds; it fails at
        # runtime with NullInjectorError the first time the screen is opened.
        if ($Ctx.FileName -eq 'api.ts' -and $line -match 'export\s+abstract\s+class\s+(\w+Api)\b') {
            $token = $Matches[1]
            if (-not (Test-BoundInConfig -Path $path -Keyword 'provide' -Name $token)) {
                Add-Violation -Ctx $Ctx -Rule 'api-token-unbound' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message "$token has no { provide: $token, useClass: Http$token } line in app.config.ts."
            }
        }
        if ($Ctx.FileName -eq 'http-api.ts' -and $line -match 'export\s+class\s+(Http\w+Api)\b') {
            $impl = $Matches[1]
            if (-not (Test-BoundInConfig -Path $path -Keyword 'useClass' -Name $impl)) {
                Add-Violation -Ctx $Ctx -Rule 'api-token-unbound' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message "$impl is never bound in app.config.ts, so the token it implements resolves to nothing."
            }
        }

        # --- component-shape --------------------------------------------
        if ($line -match '@Component\s*\(\s*\{') {
            $end = [Math]::Min($lines.Count - 1, $i + 25)
            $block = New-Object System.Collections.Generic.List[string]
            for ($k = $i; $k -le $end; $k++) {
                $block.Add($lines[$k])
                if ($lines[$k] -match '^\s*\}\)') { break }
            }
            $decorator = $block -join ' '

            if ($decorator -notmatch 'standalone\s*:\s*true') {
                Add-Violation -Ctx $Ctx -Rule 'component-shape' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'Declare standalone: true explicitly - every component in this codebase does.'
            }
            if ($decorator -match "selector\s*:\s*'([^']+)'") {
                $sel = $Matches[1]
                if ($sel -notmatch '^sa-') {
                    Add-Violation -Ctx $Ctx -Rule 'component-shape' -Line ($i + 1) -Severity 'CONVENTION' `
                        -Message "Selector '$sel' needs the sa- prefix (angular.json prefix: sa)."
                }
            }
            if ($decorator -match 'styleUrls\s*:') {
                Add-Violation -Ctx $Ctx -Rule 'component-shape' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'Use the singular styleUrl with one .css sibling.'
            }
            if ($decorator -match "styleUrl\s*:\s*'[^']*\.scss'") {
                Add-Violation -Ctx $Ctx -Rule 'component-shape' -Line ($i + 1) -Severity 'CONVENTION' `
                    -Message 'Component styles are .css - only the global styles.scss is SCSS.'
            }
        }
    }
}
