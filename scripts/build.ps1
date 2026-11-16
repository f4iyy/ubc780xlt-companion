# Compile the application (no tests). Usage: scripts\build.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

& .\mvnw.cmd -B clean compile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host 'Build OK: target\classes'
