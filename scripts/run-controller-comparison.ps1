param(
    [int]$Packets = 100,
    [int]$Payload = 256,
    [long]$Seed = 20261025,
    [string]$OutputDir = "results/comparison",
    [string]$Scenario = "localhost",
    [string]$Network = "application-emulation",
    [long]$DropSequence = -1,
    [int]$DropCount = 0,
    [long]$DelaySequence = -1,
    [int]$DelayMs = 0
)

$ErrorActionPreference = "Stop"
$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$targetClasses = Join-Path $projectRoot "target/classes"
$resolvedOutput = Join-Path $projectRoot $OutputDir
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null

foreach ($controller in @("fixed", "aimd", "adaptive")) {
    $output = Join-Path $resolvedOutput ($controller + ".csv")
    Write-Host "Running $controller with packets=$Packets payload=$Payload seed=$Seed"
    & java -cp $targetClasses edu.adaptivecc.experiments.BenchmarkMain `
        "--controller=$controller" `
        "--packets=$Packets" `
        "--payload=$Payload" `
        "--seed=$Seed" `
        "--scenario=$Scenario" `
        "--network=$Network" `
        "--drop-sequence=$DropSequence" `
        "--drop-count=$DropCount" `
        "--delay-sequence=$DelaySequence" `
        "--delay-ms=$DelayMs" `
        "--output=$output"
    if ($LASTEXITCODE -ne 0) { throw "Benchmark failed for controller: $controller" }
}

Write-Host "Combining benchmark summaries"
& python (Join-Path $projectRoot "analysis/compare_results.py") `
    (Join-Path $resolvedOutput "fixed.csv") `
    (Join-Path $resolvedOutput "aimd.csv") `
    (Join-Path $resolvedOutput "adaptive.csv") `
    "--output-dir" (Join-Path $resolvedOutput "report")
if ($LASTEXITCODE -ne 0) { throw "Comparison report generation failed" }
