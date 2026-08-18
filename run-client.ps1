# ─────────────────────────────────────────────────────────────────────────────
# run-client.ps1  —  start a Ludo-T client (its own process)
#
# Usage:
#     powershell -ExecutionPolicy Bypass -File run-client.ps1
#     powershell -ExecutionPolicy Bypass -File run-client.ps1 -ServerHost 192.168.1.20
#
# Run two of these against one server and drive a game from one: the other shows
# the board changing without being asked. Use -ServerHost to prove it across two
# machines.
#
# NOTE: this currently launches the keyboard-driven SmokeClient. The Swing GUI
# replaces it in the next step; the switch is one line here.
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$ServerHost = "localhost",
    [int]$Port = 5599,
    [switch]$SkipBuild
)

$Root   = $PSScriptRoot
$OutDir = "$Root\out"

if (-not $SkipBuild) {
    & powershell -ExecutionPolicy Bypass -File "$Root\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

Write-Host "`nConnecting to $ServerHost`:$Port..." -ForegroundColor Cyan
& java -cp $OutDir client.SmokeClient $ServerHost $Port
