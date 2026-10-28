$MavenArguments = $args
$env:MAVEN_OPTS = '-Xms64m -Xmx768m -XX:ActiveProcessorCount=4'
$ErrorActionPreference = 'Stop'
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $mavenExecutable = $maven.Source }
else {
    $candidates = @(Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Sort-Object FullName)
    if ($candidates.Count -eq 0) { throw 'Install Apache Maven 3.9.x or add mvn.cmd to PATH.' }
    $preferred = @($candidates | Where-Object { $_.FullName -match 'apache-maven-3\.9\.11\\bin\\mvn.cmd$' })
    $mavenExecutable = if ($preferred.Count -gt 0) { $preferred[0].FullName } else { $candidates[-1].FullName }
}
# Windows PowerShell treats benign native stderr as ErrorRecord. Preserve the
# actual native exit code rather than converting a JVM warning into a failure.
$ErrorActionPreference = 'Continue'
& $mavenExecutable @MavenArguments 2>&1 | ForEach-Object { $_.ToString() }
$nativeExitCode = $LASTEXITCODE
exit $nativeExitCode
