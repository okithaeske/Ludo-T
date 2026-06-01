
# ─────────────────────────────────────────────────────────────────────────────
# coverage.ps1  —  run tests under JaCoCo and open an HTML coverage report
#
# Usage:
#     .\coverage.ps1
#
# Output:  coverage-report\index.html
#
# NOTE: Recompiles production + test sources at --release 23 so JaCoCo can
# instrument the class files.  The regular IntelliJ build is not modified.
# ─────────────────────────────────────────────────────────────────────────────

$Root        = $PSScriptRoot
$SrcMain     = "$Root\src"
$SrcTest     = "$Root\src\test\test"
$OutCov      = "$Root\out\coverage"        # isolated output for coverage build
$OutCovTest  = "$Root\out\coverage-test"
$LibDir      = "$Root\lib"
$JunitJar    = "$LibDir\junit-standalone.jar"
$JacocoAgent = "$LibDir\jacocoagent.jar"
$JacocoCli   = "$LibDir\jacococli.jar"
$ExecFile    = "$Root\coverage.exec"
$ReportDir   = "$Root\coverage-report"

# ── 1. Download JUnit 5 if missing ───────────────────────────────────────────
if (-not (Test-Path $JunitJar)) {
    Write-Host "Downloading JUnit 5..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $LibDir | Out-Null
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar" `
                      -OutFile $JunitJar -UseBasicParsing
}

# ── 2. Download JaCoCo if missing ────────────────────────────────────────────
if (-not (Test-Path $JacocoAgent)) {
    Write-Host "Downloading JaCoCo..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $LibDir | Out-Null
    $zip = "$LibDir\jacoco.zip"
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/jacoco/jacoco/0.8.12/jacoco-0.8.12.zip" `
                      -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath "$LibDir\jacoco-extracted" -Force
    Copy-Item "$LibDir\jacoco-extracted\lib\jacocoagent.jar" $JacocoAgent
    Copy-Item "$LibDir\jacoco-extracted\lib\jacococli.jar"   $JacocoCli
    Remove-Item $zip
    Write-Host "JaCoCo ready." -ForegroundColor Green
}

# ── 3. Compile production sources at --release 23 ────────────────────────────
# JaCoCo 0.8.12 supports up to Java 23 class files.
# Java 25 class files (default) cause IllegalClassFormatException.
Write-Host "`nCompiling production sources (--release 23)..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $OutCov | Out-Null

$mainFiles = @(Get-ChildItem -Path $SrcMain -Filter "*.java" -Recurse |
               Where-Object { $_.FullName -notlike "*\test\*" } |
               Select-Object -ExpandProperty FullName)

& javac --release 23 -d $OutCov $mainFiles
if ($LASTEXITCODE -ne 0) { Write-Host "Production compile FAILED." -ForegroundColor Red; exit 1 }

# ── 4. Compile test sources at --release 23 ───────────────────────────────────
Write-Host "Compiling test sources (--release 23)..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $OutCovTest | Out-Null

$testFiles = @(Get-ChildItem -Path $SrcTest -Filter "*.java" -Recurse |
               Select-Object -ExpandProperty FullName)

& javac --release 23 -cp "$OutCov;$JunitJar" -d $OutCovTest $testFiles
if ($LASTEXITCODE -ne 0) { Write-Host "Test compile FAILED." -ForegroundColor Red; exit 1 }
Write-Host "Compiled OK." -ForegroundColor Green

# ── 5. Run tests with JaCoCo agent ───────────────────────────────────────────
Write-Host "`nRunning tests with coverage instrumentation..." -ForegroundColor Cyan
Remove-Item $ExecFile -ErrorAction SilentlyContinue

$includes = "engine/*:enums/*:logger/*:model/*:player/*"
& java "-javaagent:$JacocoAgent=destfile=$ExecFile,includes=$includes" `
       -jar $JunitJar `
       "--classpath=$OutCov" `
       "--classpath=$OutCovTest" `
       --scan-class-path `
       "--include-package=test.test" `
       --details=summary

if ($LASTEXITCODE -ne 0) { Write-Host "Tests FAILED - report may be incomplete." -ForegroundColor Yellow }

# ── 6. Generate HTML report ───────────────────────────────────────────────────
Write-Host "`nGenerating HTML coverage report..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null

& java -jar $JacocoCli report $ExecFile `
       --classfiles $OutCov `
       --sourcefiles $SrcMain `
       --html $ReportDir `
       --xml "$ReportDir\jacoco.xml" `
       --name "Ludo-T Coverage"

Write-Host "`nReport ready: $ReportDir\index.html" -ForegroundColor Green
Write-Host "XML report:   $ReportDir\jacoco.xml  (used by SonarQube)" -ForegroundColor Green

# ── 7. Open in browser ────────────────────────────────────────────────────────
Start-Process "$ReportDir\index.html"
