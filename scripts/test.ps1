# Unit + integration tests, reports and the 80% coverage gate. Usage: scripts\test.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

& .\mvnw.cmd -B verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host 'Test report     : target\reports\tests\tests.html'
Write-Host 'Coverage report : target\reports\coverage\index.html'
