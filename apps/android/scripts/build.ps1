param(
    [string[]]$Tasks = @(':domain:test', ':app:assembleDebug'),
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$androidRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $androidRoot
$candidate = Join-Path $env:USERPROFILE '.jdks/ms-21.0.10'
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    if (Test-Path $candidate) { $env:JAVA_HOME = $candidate }
    else { throw 'Set JAVA_HOME to an installed JDK 21.' }
}
$javaVersion = & (Join-Path $env:JAVA_HOME 'bin/java.exe') --version 2>&1 | Out-String
if ($javaVersion -notmatch '(openjdk|java)\s+(version\s+)?"?21\.') {
    if (Test-Path $candidate) {
        $env:JAVA_HOME = $candidate
        $javaVersion = & (Join-Path $env:JAVA_HOME 'bin/java.exe') --version 2>&1 | Out-String
    }
}
if ($javaVersion -notmatch '(openjdk|java)\s+(version\s+)?"?21\.') { throw 'JAVA_HOME must select JDK 21.' }
$env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
$env:GRADLE_USER_HOME = Join-Path $androidRoot '.cache/gradle'
$existingCache = Join-Path $env:USERPROFILE '.gradle/caches'
if ($Offline -and (Test-Path (Join-Path $existingCache 'modules-2'))) { $env:GRADLE_RO_DEP_CACHE = $existingCache }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$installedGradle = Join-Path $env:USERPROFILE '.gradle/gradle-8.11.1/gradle-8.11.1/bin/gradle.bat'
$gradleCommand = Join-Path $androidRoot 'gradlew.bat'
if (($Offline -and (Test-Path $installedGradle)) -or -not (Test-Path $gradleCommand)) {
    if (-not (Test-Path $installedGradle)) { throw 'Gradle wrapper or Gradle 8.11.1 is required.' }
    $gradleCommand = $installedGradle
}
New-Item -ItemType Directory -Force 'evidence' | Out-Null
$arguments = @('--no-daemon')
if ($Offline) { $arguments += '--offline' }
$arguments += $Tasks
# Windows PowerShell promotes native stderr into ErrorRecord. Preserve Gradle's
# exit code instead of treating benign JVM/compiler warnings as terminating errors.
$ErrorActionPreference = 'Continue'
& $gradleCommand @arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath ('evidence/gradle-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
$gradleExitCode = $LASTEXITCODE
exit $gradleExitCode
