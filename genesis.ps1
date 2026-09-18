param(
    [ValidateSet('help', 'list', 'build', 'check', 'test', 'serve', 'gallery')]
    [string]$Task = 'help',
    [string]$Target = '',
    [ValidateRange(1, 65535)][int]$Port = 8787,
    [switch]$VerboseOutput
)

$ErrorActionPreference = 'Stop'
$targets = @('core', 'continents', 'hydrology-labs', 'tectonic', 'rivers', 'tectonic-viewer', 'refinement', 'geology', 'all')

function Show-Usage {
    Write-Host 'Genesis terrain lab'
    Write-Host '  .\genesis.ps1 check [target]       # default: tectonic'
    Write-Host '  .\genesis.ps1 gallery [target]     # default: tectonic-viewer'
    Write-Host '  .\genesis.ps1 serve -Port 8787'
    Write-Host '  .\genesis.ps1 build                # Java 8 core JAR only'
    Write-Host '  .\genesis.ps1 list'
    Write-Host '  add -VerboseOutput to stream tool/test output'
}

function Invoke-QuietNative([string]$Label, [string]$LogName, [string]$File, [string[]]$Arguments) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    $log = Join-Path 'build/logs' ($LogName + '-' + $PID + '.log')
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($VerboseOutput) {
            & $File @Arguments
        } else {
            & $File @Arguments *> $log
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $timer.Stop()
    if ($code -ne 0) {
        if (-not $VerboseOutput -and (Test-Path $log)) { Get-Content $log -Tail 80 }
        throw "$Label failed (exit $code; log: $log)"
    }
    Write-Host ('OK  {0} ({1:N1}s)' -f $Label, $timer.Elapsed.TotalSeconds)
}

if ($Task -eq 'help') { Show-Usage; exit 0 }
if ($Task -eq 'list') { $targets | ForEach-Object { Write-Host $_ }; exit 0 }
if (($Task -eq 'check' -or $Task -eq 'test') -and -not $Target) { $Target = if ($Task -eq 'test') { 'all' } else { 'tectonic' } }
if ($Task -eq 'gallery' -and -not $Target) { $Target = 'tectonic-viewer' }
if ($Target -and $targets -notcontains $Target) { throw "Unknown target '$Target'. Run .\genesis.ps1 list." }
if (($Task -eq 'build' -or $Task -eq 'serve') -and $Target) { throw "$Task does not accept a target." }

Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path 'build/classes', 'build/gallery', 'build/logs' | Out-Null
    $coreSources = @(Get-ChildItem core/src/main/java -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { $_.FullName })
    $coreArgs = @('--release', '8', '-encoding', 'UTF-8', '-d', 'build/classes') + $coreSources
    Invoke-QuietNative 'compile core' 'compile-core' 'javac' $coreArgs

    if ($Task -eq 'build') {
        Invoke-QuietNative 'package core JAR' 'package-core' 'jar' @('--create', '--file', 'build/genesis-core.jar', '-C', 'build/classes', 'genesis/core')
        exit 0
    }

    $sourceRoots = @('oracle/src', 'harness/server/src')
    if ($Task -in @('check', 'test', 'gallery')) { $sourceRoots += 'harness/gates/src' }
    $harnessSources = @(Get-ChildItem $sourceRoots -Recurse -Filter '*.java' | Sort-Object FullName | ForEach-Object { $_.FullName })
    $harnessArgs = @('--release', '21', '-encoding', 'UTF-8', '-cp', 'build/classes', '-d', 'build/classes') + $harnessSources
    Invoke-QuietNative ($(if ($Task -eq 'serve') { 'compile viewer' } else { 'compile gates' })) 'compile-harness' 'javac' $harnessArgs

    switch ($Task) {
        { $_ -in @('check', 'test') } {
            Invoke-QuietNative "check $Target" "check-$Target" 'java' @('-Djava.awt.headless=true', '-cp', 'build/classes', 'genesis.harness.Gates', $Target)
        }
        'gallery' {
            Invoke-QuietNative "gallery $Target" "gallery-$Target" 'java' @('-Djava.awt.headless=true', '-cp', 'build/classes', 'genesis.harness.Gates', 'gallery', $Target)
        }
        'serve' {
            Write-Host "SERVE http://localhost:$Port/ (Ctrl+C to stop)"
            & java '-Djava.awt.headless=true' -cp build/classes genesis.harness.Server $Port
            if ($LASTEXITCODE -ne 0) { throw "serve failed (exit $LASTEXITCODE)" }
        }
    }
} finally {
    Pop-Location
}
