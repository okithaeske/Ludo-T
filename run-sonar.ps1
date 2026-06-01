# run-sonar.ps1 -- generate coverage then push to SonarQube / SonarCloud
#
# Prerequisites:
#   1. SonarScanner CLI installed and on PATH
#      Download: https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner/
#   2. sonar-project.properties has sonar.host.url and sonar.token filled in
#
# Usage:
#   .\run-sonar.ps1

$Root = $PSScriptRoot

# Step 1: Run coverage to produce jacoco.xml
Write-Host "Running coverage (generates jacoco.xml for SonarQube)..." -ForegroundColor Cyan
& "$Root\coverage.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Coverage step failed -- continuing anyway." -ForegroundColor Yellow
}

# Step 2: Locate sonar-scanner (PATH or known install location)
$scannerCmd = Get-Command sonar-scanner -ErrorAction SilentlyContinue
if ($scannerCmd) {
    $scannerExe = "sonar-scanner"
} elseif (Test-Path "C:\sonar-scanner\bin\sonar-scanner.bat") {
    $scannerExe = "C:\sonar-scanner\bin\sonar-scanner.bat"
    Write-Host "Found sonar-scanner at C:\sonar-scanner\bin" -ForegroundColor Yellow
    Write-Host "(Tip: add C:\sonar-scanner\bin to your PATH to skip this detection)" -ForegroundColor DarkGray
} else {
    Write-Host ""
    Write-Host "ERROR: sonar-scanner not found in PATH or C:\sonar-scanner\bin." -ForegroundColor Red
    Write-Host ""
    Write-Host "Install SonarScanner CLI:" -ForegroundColor Yellow
    Write-Host "  1. Download: https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner/"
    Write-Host "  2. Extract to C:\sonar-scanner"
    Write-Host "  3. Add C:\sonar-scanner\bin to your PATH"
    exit 1
}

# Step 3: Run the scanner
Write-Host ""
Write-Host "Running SonarScanner..." -ForegroundColor Cyan
& $scannerExe "-Dsonar.projectBaseDir=$Root"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Sonar analysis complete. View results at http://localhost:9000" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "SonarScanner exited with code $LASTEXITCODE." -ForegroundColor Red
    exit $LASTEXITCODE
}
