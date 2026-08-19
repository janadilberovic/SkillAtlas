# guard.ps1 - entry point for the SkillAtlas guard.
#
# Two ways in:
#   hook mode   stdin carries the PostToolUse payload; only the lines this edit
#               touched can produce a violation. exit 2 feeds the report back to
#               Claude as advisory feedback (the edit is NOT reverted).
#   dry run     -Path <file> [-All]; reads the file directly, no stdin. -All
#               ignores the touched-lines filter and shows everything a rule
#               would find. This is how a rule is proven before it is enabled.
#
# Escape hatch: SKILLATLAS_GUARD_OFF=1 disables the guard entirely.
#
# Ordering matters for latency. The hook fires on every Edit/Write, most of which
# are .md or .json this guard has nothing to say about. Windows PowerShell 5.1
# costs ~570 ms to start before a single line of this file runs, so everything
# skippable is skipped before any dot-sourcing happens.

param(
    [string]$Path,
    [switch]$All
)

$ErrorActionPreference = 'Stop'

if ($env:SKILLATLAS_GUARD_OFF -eq '1') { exit 0 }

# ------------------------------------------------------ resolve the target ---

$payload = $null
$target = $Path

if ($Path) {
    $All = $true                      # a dry run always inspects the whole file
} else {
    try { $payload = [Console]::In.ReadToEnd() | ConvertFrom-Json } catch { exit 0 }
    if ($null -eq $payload) { exit 0 }
    $target = $payload.tool_input.file_path
}

if ([string]::IsNullOrWhiteSpace($target)) { exit 0 }

# ------------------------------------------------------------- cheap gate ---

$ext = [System.IO.Path]::GetExtension($target).ToLowerInvariant()
$isJava = ($ext -eq '.java')
$isFrontend = ($ext -in @('.ts', '.html', '.css', '.scss')) -and ($target -match '[\\/]frontend[\\/]src[\\/]')

if (-not ($isJava -or $isFrontend)) { exit 0 }
if (-not (Test-Path -LiteralPath $target)) { exit 0 }

# ---------------------------------------------------------------- analyse ---

. "$PSScriptRoot\guard-lib.ps1"

$touched = ''
if (-not $Path) {
    $touched = Get-TouchedContent -Payload $payload
    if (-not $touched) { exit 0 }     # nothing was written; nothing to judge
}

$lines = @(Get-Content -LiteralPath $target)
if ($lines.Count -eq 0) { exit 0 }

$ctx = New-GuardContext -Path $target -Lines $lines -TouchedContent $touched -All:$All

if ($isJava) {
    . "$PSScriptRoot\rules-java.ps1"
    Invoke-JavaRules -Ctx $ctx
} else {
    . "$PSScriptRoot\rules-frontend.ps1"
    Invoke-FrontendRules -Ctx $ctx
}

# ----------------------------------------------------------------- report ---

$report = Format-GuardReport -Ctx $ctx
if (-not $report) { exit 0 }

if ($Path) {
    Write-Output $report            # dry run: stdout, exit 0, purely informational
    exit 0
}

[Console]::Error.WriteLine($report)
exit 2
