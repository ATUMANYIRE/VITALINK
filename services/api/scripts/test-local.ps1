$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'start-database.ps1')
. (Join-Path $PSScriptRoot 'environment.ps1')
Push-Location $apiRoot
try {
    & (Join-Path $PSScriptRoot 'maven.ps1') -o test
    $testExit = $LASTEXITCODE
} finally { Pop-Location }
exit $testExit
