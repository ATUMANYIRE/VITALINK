param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin')
$databasePath = Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime/postgres'
& (Join-Path $PostgresBin 'pg_ctl.exe') stop -D $databasePath -m fast -w -t 30
exit $LASTEXITCODE
