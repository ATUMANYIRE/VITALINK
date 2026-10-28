$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'environment.ps1')
$jar = Join-Path $apiRoot 'target/vitalink-api-0.1.0.jar'
if (-not (Test-Path $jar)) { throw 'Package API first: scripts/maven.ps1 -o package' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar $jar
exit $LASTEXITCODE
