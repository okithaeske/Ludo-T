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
$H2Jar    = "$Root\lib\h2.jar"

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
# Wiped first. javac only ever adds .class files, so a test that is renamed, moved to another
# package or deleted leaves its old class behind and JUnit keeps running it from the stale
# copy. That is how the three-way split into unit/integration/automation briefly reported 387
# tests instead of 192 - every suite was discovered twice, under both its old and new package.
if (Test-Path $OutTest) {
    Remove-Item -Recurse -Force $OutTest
}
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
# h2.jar only on the RUN classpath, never the compile one: the persistence tests load
# the driver through JDBC service discovery, exactly as the server does. When the jar is
# absent PersistenceTierTest aborts itself rather than failing the suite.
$junitArgs = @(
    "--classpath=$OutMain"
    "--classpath=$OutTest"
)
if (Test-Path $H2Jar) { $junitArgs += "--classpath=$H2Jar" }
$junitArgs += @("--scan-class-path", "--include-package=test", "--details=tree")

& java -jar $JunitJar @junitArgs

exit $LASTEXITCODE
