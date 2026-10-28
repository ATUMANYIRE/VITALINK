$apiRoot = Split-Path $PSScriptRoot -Parent
$runtimePath = Join-Path $apiRoot '.runtime'
$configPath = Join-Path $runtimePath 'demo-credentials.json'
if (-not (Test-Path $configPath)) { throw 'Run scripts/start-database.ps1 first.' }
$config = Get-Content -Raw $configPath | ConvertFrom-Json
$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:$($config.database.port)/$($config.database.name)"
$env:DATABASE_USER = $config.database.username
$env:DATABASE_PASSWORD = $config.database.password
$env:VITALINK_BOOTSTRAP_USERS = ConvertTo-Json -InputObject @($config.users) -Depth 5 -Compress
if (-not $env:API_PORT) { $env:API_PORT = '8087' }
$installedJdk = 'C:\Users\HP\.jdks\ms-21.0.10'
if (Test-Path $installedJdk) { $env:JAVA_HOME = $installedJdk }
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
$javaVersion = Get-Content -Raw (Join-Path $env:JAVA_HOME 'release')
if ($javaVersion -notmatch 'JAVA_VERSION="21\.') { throw 'This project requires JDK 21.' }
$env:MAVEN_SKIP_RC = '1'
