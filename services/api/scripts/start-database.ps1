param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference = 'Stop'
$apiRoot = Split-Path $PSScriptRoot -Parent
$runtimePath = Join-Path $apiRoot '.runtime'
& python (Join-Path $PSScriptRoot 'generate-dev-config.py')
if ($LASTEXITCODE -ne 0) { throw 'Could not generate development config.' }
$config = Get-Content -Raw (Join-Path $runtimePath 'demo-credentials.json') | ConvertFrom-Json
$databasePath = Join-Path $runtimePath 'postgres'
if (-not (Test-Path (Join-Path $databasePath 'PG_VERSION'))) {
    & (Join-Path $PostgresBin 'initdb.exe') -D $databasePath -U $config.database.username -A scram-sha-256 --pwfile (Join-Path $runtimePath 'db-password.txt') --encoding UTF8 --locale C
    if ($LASTEXITCODE -ne 0) { throw 'initdb failed.' }
}
& (Join-Path $PostgresBin 'pg_isready.exe') -h 127.0.0.1 -p $config.database.port *> $null
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $PostgresBin 'pg_ctl.exe') start -D $databasePath -l (Join-Path $runtimePath 'postgres.log') -o "-p $($config.database.port) -h 127.0.0.1" -w -t 30
    if ($LASTEXITCODE -ne 0) {
        # pg_ctl's Windows token helper may be unavailable in a restricted process host.
        # Run the same server binary normally, without privilege changes or sandbox overrides.
        $processArgs = @('-D', ('"' + $databasePath + '"'), '-p', $config.database.port, '-h', '127.0.0.1')
        Start-Process -FilePath (Join-Path $PostgresBin 'postgres.exe') -ArgumentList $processArgs -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtimePath 'postgres-stdout.log') -RedirectStandardError (Join-Path $runtimePath 'postgres-stderr.log') | Out-Null
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            & (Join-Path $PostgresBin 'pg_isready.exe') -h 127.0.0.1 -p $config.database.port *> $null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'PostgreSQL failed to start; inspect .runtime/postgres-stderr.log.' }
    }
}
$env:PGPASSWORD = $config.database.password
try {
    $exists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $config.database.port -U $config.database.username -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='vitalink'"
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL login failed.' }
    if ($exists -ne '1') {
        & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $config.database.port -U $config.database.username vitalink
        if ($LASTEXITCODE -ne 0) { throw 'Database creation failed.' }
    }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
Write-Output 'Project PostgreSQL ready on 127.0.0.1:55432.'
