$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'environment.ps1')
$jar = Join-Path $apiRoot 'target/vitalink-api-0.1.0.jar'
if (-not (Test-Path $jar)) { throw 'Package API first.' }
$process = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @('-Xms128m','-Xmx512m','-jar',('"'+$jar+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimePath 'api-stdout.log') -RedirectStandardError (Join-Path $runtimePath 'api-stderr.log')
$process.Id | Set-Content (Join-Path $runtimePath 'api.pid')
Write-Output "Project API process started (PID $($process.Id)); wait for /actuator/health/readiness before demonstration."
