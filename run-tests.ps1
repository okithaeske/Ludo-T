# ─────────────────────────────────────────────────────────────────────────────
# run-tests.ps1  —  compile & run the JUnit 5 test suite
#
# Usage (from the project root):
#     powershell -ExecutionPolicy Bypass -File run-tests.ps1
#
# First run downloads junit-platform-console-standalone.jar into lib\.
# Every subsequent run recompiles tests and executes them.
# ─────────────────────────────────────────────────────────────────────────────

$Root     = $PSScriptRoot
$SrcTest  = "$Root\test"
$OutMain  = "$Root\out"
$OutTest  = "$Root\out\test"
$JunitJar = "$Root\lib\junit-standalone.jar"

# ── 1. Download JUnit 5 Console Standalone if missing ────────────────────────
if (-not (Test-Path $JunitJar)) {
    Write-Host "Downloading JUnit 5 Console Standalone..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path "$Root\lib" | Out-Null
    $url = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar"
    Invoke-WebRequest -Uri $url -OutFile $JunitJar -UseBasicParsing
    Write-Host "Downloaded." -ForegroundColor Green
}

# ── 2. Compile test sources ───────────────────────────────────────────────────
Write-Host "`nCompiling test sources..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $OutTest | Out-Null

$testFiles = @(Get-ChildItem -Path $SrcTest -Filter "*.java" -Recurse |
               Select-Object -ExpandProperty FullName)

if ($testFiles.Count -eq 0) {
    Write-Host "No .java files found in $SrcTest" -ForegroundColor Red; exit 1
}

& javac -cp "$OutMain;$JunitJar" -d $OutTest $testFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nCompilation FAILED." -ForegroundColor Red; exit 1
}
Write-Host "Compiled $($testFiles.Count) file(s)." -ForegroundColor Green

# ── 3. Run all tests ──────────────────────────────────────────────────────────
Write-Host "`nRunning tests...`n" -ForegroundColor Cyan
& java -jar $JunitJar `
    "--classpath=$OutMain" `
    "--classpath=$OutTest" `
    --scan-class-path `
    "--include-package=test" `
    --details=tree

exit $LASTEXITCODE
