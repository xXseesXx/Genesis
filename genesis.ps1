param(
    [ValidateSet('build', 'test', 'serve', 'gallery')][string]$Task = 'test',
    [int]$Port = 8787
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path 'build/classes', 'build/gallery' | Out-Null
    $coreSources = @(Get-ChildItem core/src/main/java -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac --release 8 -encoding UTF-8 -d build/classes @coreSources
    if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
    $harnessSources = @(Get-ChildItem harness/server/src, harness/gates/src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac --release 21 -encoding UTF-8 -cp build/classes -d build/classes @harnessSources
    if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed' }
    & jar --create --file build/genesis-core.jar -C build/classes genesis/core
    if ($LASTEXITCODE -ne 0) { throw 'Core packaging failed' }
    switch ($Task) {
        'test' {
            & java '-Djava.awt.headless=true' -cp build/classes genesis.harness.Gates
            if ($LASTEXITCODE -ne 0) { throw 'Gates failed' }
        }
        'serve' { & java '-Djava.awt.headless=true' -cp build/classes genesis.harness.Server $Port }
        'gallery' { & java '-Djava.awt.headless=true' -cp build/classes genesis.harness.Gates gallery }
    }
    if ($LASTEXITCODE -ne 0) { throw "Task failed: $Task" }
} finally { Pop-Location }
