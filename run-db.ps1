# ─────────────────────────────────────────────────────────────────────────────
# run-db.ps1  —  start the Ludo-T database tier (its own process)
#
# This is the third of the three applications the Architecture rubric asks for:
#     run-db.ps1      database tier   (H2, TCP port 9092)
#     run-server.ps1  server tier     (port 5599)
#     run-client.ps1  client tier     (Swing GUI)
#
# Usage:
#     powershell -ExecutionPolicy Bypass -File run-db.ps1 -Init    # first time
#     powershell -ExecutionPolicy Bypass -File run-db.ps1          # every time after
#     powershell -ExecutionPolicy Bypass -File run-db.ps1 -Reset   # back to initial state
#
# -Init   applies db\schema.sql then db\seed.sql, then serves.
# -Reset  does the same thing; schema.sql drops every object first, so the two are
#         the same command with different intent. Run -Reset before a demonstration:
#         the brief requires the database to be in its initial state, ready to run
#         without delay.
# -Web    also starts the H2 Console on http://localhost:8082, which is a convenient
#         way to show the tutor the rows arriving as games finish.
#
# H2 runs in *server* mode rather than embedded on purpose. Embedded would make the
# database a library inside the server process, which is precisely what the top band
# of the Architecture criterion does not want.
# ─────────────────────────────────────────────────────────────────────────────

param(
    [int]$Port = 9092,
    [switch]$Init,
    [switch]$Reset,
    [switch]$Web
)

$Root   = $PSScriptRoot
$LibDir = "$Root\lib"
$DbDir  = "$Root\db"
$H2Jar  = "$LibDir\h2.jar"

# ── 1. Download H2 if missing (same pattern run-tests.ps1 uses for JUnit) ─────
if (-not (Test-Path $H2Jar)) {
    Write-Host "Downloading H2 database..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $LibDir | Out-Null
    $url = "https://repo1.maven.org/maven2/com/h2database/h2/2.3.232/h2-2.3.232.jar"
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $H2Jar -UseBasicParsing
    Write-Host "Downloaded." -ForegroundColor Green
}

# The database file lives in db\, next to the SQL that builds it.
$DbFile = "$DbDir\ludo"
# Backslashes are escape characters inside a JDBC URL, so H2 wants forward slashes
# even on Windows. The project path has a space in it, which H2 handles fine.
$Url    = "jdbc:h2:file:" + $DbFile.Replace('\', '/')

# ── 2. Apply the SQL, if asked ───────────────────────────────────────────────
# Done against the file directly, before the TCP server binds, so there is no
# question of the schema changing under a connected server.
if ($Init -or $Reset) {
    foreach ($script in @("$DbDir\schema.sql", "$DbDir\seed.sql")) {
        if (-not (Test-Path $script)) {
            Write-Host "Missing $script" -ForegroundColor Red; exit 1
        }
        Write-Host "Applying $(Split-Path $script -Leaf)..." -ForegroundColor Cyan
        # No -password flag: H2's argument parser swallows an empty value and then
        # reads -script as the password. Omitting it defaults sa to no password.
        & java -cp $H2Jar org.h2.tools.RunScript -url $Url -user sa -script $script
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Failed applying $script" -ForegroundColor Red; exit 1
        }
    }
    Write-Host "Database is in its initial state." -ForegroundColor Green
}

if (-not (Test-Path "$DbFile.mv.db")) {
    Write-Host ""
    Write-Host "No database file yet. Run once with -Init:" -ForegroundColor Yellow
    Write-Host "    .\run-db.ps1 -Init" -ForegroundColor Yellow
    exit 1
}

# ── 3. Serve ─────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "Starting H2 on tcp://localhost:$Port ..." -ForegroundColor Cyan
Write-Host "  database   $DbFile" -ForegroundColor DarkGray
Write-Host "  JDBC URL   jdbc:h2:tcp://localhost:$Port/ludo" -ForegroundColor DarkGray
Write-Host "  user       sa  (no password)" -ForegroundColor DarkGray
if ($Web) {
    Write-Host "  console    http://localhost:8082" -ForegroundColor DarkGray
}
Write-Host "  press Ctrl+C to stop" -ForegroundColor DarkGray
Write-Host ""

$h2Args = @("-tcp", "-tcpPort", "$Port", "-baseDir", "$DbDir", "-ifExists")
if ($Web) { $h2Args += @("-web", "-webPort", "8082") }

& java -cp $H2Jar org.h2.tools.Server @h2Args
