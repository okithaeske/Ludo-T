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
    [string]$Db = "jdbc:h2:tcp://localhost:9092/ludo",
    [switch]$SkipBuild
)

$Root   = $PSScriptRoot
$OutDir = "$Root\out"
$H2Jar  = "$Root\lib\h2.jar"

if (-not $SkipBuild) {
    & powershell -ExecutionPolicy Bypass -File "$Root\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

# h2.jar is on the RUNTIME classpath only. Nothing under src\ imports an H2 class —
# persistence\ speaks java.sql and the driver is found by JDBC service discovery — so
# build.ps1 needs no classpath at all. That is the compile-time proof that the server
# depends on the database through the JDBC interface rather than on H2 itself.
$ClassPath = if (Test-Path $H2Jar) { "$OutDir;$H2Jar" } else { $OutDir }
if (-not (Test-Path $H2Jar)) {
    Write-Host "lib\h2.jar not found - the server will start without a database tier." -ForegroundColor Yellow
    Write-Host "Run .\run-db.ps1 -Init once to download it and build the database." -ForegroundColor Yellow
}

# Pass -Db off to run with no database tier at all.
Write-Host "`nStarting server on port $Port..." -ForegroundColor Cyan
& java -cp $ClassPath server.ServerMain `
    "--port=$Port" `
    "--workers=$Workers" `
    "--queue=$QueueCapacity" `
    "--metrics=$MetricsIntervalMillis" `
    "--db=$Db"
