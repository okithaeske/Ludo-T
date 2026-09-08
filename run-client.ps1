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
# This launches the Swing GUI (client.ClientMain). The older keyboard-driven console
# client is still there if you want it: -Smoke swaps to client.SmokeClient.
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$ServerHost = "localhost",
    [int]$Port = 5599,
    [switch]$SkipBuild,
    [switch]$Smoke
)

$Root   = $PSScriptRoot
$OutDir = "$Root\out"

if (-not $SkipBuild) {
    & powershell -ExecutionPolicy Bypass -File "$Root\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

Write-Host "`nConnecting to $ServerHost`:$Port..." -ForegroundColor Cyan
$MainClass = if ($Smoke) { "client.SmokeClient" } else { "client.ClientMain" }
& java -cp $OutDir $MainClass $ServerHost $Port
