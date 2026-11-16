# Build the runtime image and the installer for the current platform with jpackage.
# Output: target\dist\ (one file per installer, never zipped).
# Usage: scripts\package.ps1                         (msi on Windows)
#        $env:PACKAGE_TYPE = 'app-image'; scripts\package.ps1   (portable folder, no WiX needed)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

& .\mvnw.cmd -B -DskipTests '-Djacoco.skip=true' package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$version = (& .\mvnw.cmd -B -q -DforceStdout help:evaluate '-Dexpression=project.version').Trim()
$appVersion = $version -replace '-SNAPSHOT$', ''
$type = if ($env:PACKAGE_TYPE) { $env:PACKAGE_TYPE } else { 'msi' }

$jpackage = 'jpackage'
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) { $jpackage = "$env:JAVA_HOME\bin\jpackage.exe" }

# A JDK that already contains JavaFX (Zulu FX, Liberica Full...) cannot be mixed with the JavaFX jars of Maven:
# jlink rejects them. Use the JavaFX of the JDK then; a plain JDK (Temurin, as on CI) gets the Maven jars.
$java = 'java'
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $java = "$env:JAVA_HOME\bin\java.exe" }
$modulePath = @('--module-path', 'target\jpackage-modules')
if ((& $java --list-modules) -match '^javafx\.controls') {
  Write-Host 'The JDK contains JavaFX: using it instead of the Maven jars.'
  $modulePath = @()
}

if (Test-Path target\dist) { Remove-Item -Recurse -Force target\dist }
New-Item -ItemType Directory -Force target\dist | Out-Null

$jpArgs = @(
  '--type', $type,
  '--name', 'UBC780XLT Companion',
  '--app-version', $appVersion,
  '--vendor', 'f4iyy',
  '--input', 'target\jpackage-input',
  '--main-jar', 'ubc780xlt-companion.jar',
  '--main-class', 'io.github.f4iyy.ubc780xlt.Launcher',
  '--add-modules', 'javafx.controls,java.logging,java.prefs,java.xml,java.management,java.naming,java.sql,java.desktop,jdk.unsupported',
  '--dest', 'target\dist'
)
$jpArgs += $modulePath
if ($type -ne 'app-image') { $jpArgs += @('--win-menu', '--win-shortcut', '--win-dir-chooser') }

& $jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host 'Package OK:'
Get-ChildItem target\dist | ForEach-Object { $_.Name }
