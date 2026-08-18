# ─────────────────────────────────────────────────────────────────────────────
# run-server.ps1  —  start the Ludo-T server tier (its own process)
#
# Usage:
#     powershell -ExecutionPolicy Bypass -File run-server.ps1
#     powershell -ExecutionPolicy Bypass -File run-server.ps1 -Port 6000 -Workers 2 -QueueCapacity 32
#
# Small worker and queue values make the request queue visibly fill during the
# load demonstration; the defaults are already sized for that.
# ─────────────────────────────────────────────────────────────────────────────

param(
    [int]$Port = 5599,
    [int]$Workers = 4,
    [int]$QueueCapacity = 256,
    [int]$MetricsIntervalMillis = 1000,
    [switch]$SkipBuild
)

$Root   = $PSScriptRoot
$OutDir = "$Root\out"

if (-not $SkipBuild) {
    & powershell -ExecutionPolicy Bypass -File "$Root\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

Write-Host "`nStarting server on port $Port..." -ForegroundColor Cyan
& java -cp $OutDir server.ServerMain `
    "--port=$Port" `
    "--workers=$Workers" `
    "--queue=$QueueCapacity" `
    "--metrics=$MetricsIntervalMillis"
