# ─────────────────────────────────────────────────────────────────────────────
# run-testclients.ps1  —  drive the server with fast automatic clients
#
# Usage (server must already be running):
#     powershell -ExecutionPolicy Bypass -File run-testclients.ps1
#     powershell -ExecutionPolicy Bypass -File run-testclients.ps1 -Clients 4 -Threads 8
#     powershell -ExecutionPolicy Bypass -File run-testclients.ps1 -Processes 2
#     powershell -ExecutionPolicy Bypass -File run-testclients.ps1 -Mix CONTROL -Csv load.csv
#
# -Processes launches that many separate JVMs, each with its own -Clients connections.
# One JVM with N sockets already gives N independent clients; separate processes additionally
# remove any doubt that the load shares a heap, a GC or a scheduler with itself — which is the
# version to run when demonstrating rather than measuring.
#
# To make the queue visibly fill, start the server small:
#     run-server.ps1 -Workers 2 -QueueCapacity 32
# then run this with -Clients 4 -Threads 8 -Inflight 128.
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$ServerHost = "localhost",
    [int]$Port = 5599,
    [int]$Clients = 2,
    [int]$Threads = 4,
    [int]$Requests = 250,
    [int]$Duration = 0,
    [int]$Games = 2,
    [string]$Mode = "LUDO_T",
    [int]$Tick = 250,
    [ValidateSet("READ", "MIXED", "CONTROL")]
    [string]$Mix = "MIXED",
    [int]$Inflight = 64,
    [long]$Seed = 20261009,
    [int]$Processes = 1,
    [string]$Csv = "",
    [switch]$Keep,
    [switch]$SkipBuild
)

$Root   = $PSScriptRoot
$OutDir = "$Root\out"

if (-not $SkipBuild) {
    & powershell -ExecutionPolicy Bypass -File "$Root\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

$flags = @(
    "--host=$ServerHost"
    "--port=$Port"
    "--clients=$Clients"
    "--threads=$Threads"
    "--requests=$Requests"
    "--duration=$Duration"
    "--games=$Games"
    "--mode=$Mode"
    "--tick=$Tick"
    "--mix=$Mix"
    "--inflight=$Inflight"
    "--seed=$Seed"
    "--keep=$($Keep.IsPresent.ToString().ToLower())"
)
if ($Csv -ne "") { $flags += "--csv=$Csv" }

Write-Host "`nLoad: $Processes process(es) x $Clients client(s) x $Threads thread(s) -> $ServerHost`:$Port" -ForegroundColor Cyan

if ($Processes -le 1) {
    & java -cp $OutDir client.testing.TestClientMain @flags
    exit $LASTEXITCODE
}

# Several JVMs: give each its own seed so they do not fire an identical command sequence in
# lockstep, and its own CSV row set, then wait for all of them before returning.
$jobs = @()
for ($i = 0; $i -lt $Processes; $i++) {
    $processFlags = $flags | Where-Object { $_ -notlike "--seed=*" }
    $processFlags += "--seed=$($Seed + $i * 1000)"
    # The classpath must be quoted: this project's path contains spaces, and Start-Process
    # re-splits its argument list on them before java ever sees it.
    $proc = Start-Process -FilePath "java" `
        -ArgumentList (@("-cp", "`"$OutDir`"", "client.testing.TestClientMain") + $processFlags) `
        -NoNewWindow -PassThru
    # Touching .Handle caches the process handle. Without it .ExitCode reads back empty once
    # the process has gone, and every run would be reported as a failure.
    $null = $proc.Handle
    $jobs += $proc
}
$jobs | ForEach-Object { $_.WaitForExit() }

$failed = @($jobs | Where-Object { $_.ExitCode -ne 0 }).Count
if ($failed -gt 0) {
    Write-Host "`n$failed of $Processes load process(es) exited non-zero." -ForegroundColor Red
    exit 1
}
Write-Host "`nAll $Processes load process(es) finished." -ForegroundColor Green
