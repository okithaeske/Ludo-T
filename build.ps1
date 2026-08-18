# ─────────────────────────────────────────────────────────────────────────────
# build.ps1  —  compile every production source into out\
#
# Usage (from the project root):
#     powershell -ExecutionPolicy Bypass -File build.ps1
#
# run-tests.ps1 compiles only the test sources and assumes out\ is current, so run
# this first after changing anything under src\.
# ─────────────────────────────────────────────────────────────────────────────

$Root   = $PSScriptRoot
$SrcDir = "$Root\src"
$OutDir = "$Root\out"

Write-Host "Compiling production sources..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$sources = @(Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse |
             Select-Object -ExpandProperty FullName)

if ($sources.Count -eq 0) {
    Write-Host "No .java files found in $SrcDir" -ForegroundColor Red; exit 1
}

& javac -d $OutDir $sources
if ($LASTEXITCODE -ne 0) {
    Write-Host "`nCompilation FAILED." -ForegroundColor Red; exit 1
}

Write-Host "Compiled $($sources.Count) file(s) into out\." -ForegroundColor Green
