param([Parameter(Mandatory=$true)][string]$Ip)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath '.venv\Scripts\python.exe')) { throw 'Run install.ps1 first.' }
& '.\.venv\Scripts\python.exe' 'scripts\setup_https.py' --ip $Ip
if ($LASTEXITCODE -ne 0) { throw 'HTTPS setup failed.' }
Write-Host 'Read README.md: trust root-ca.cer on both devices, then start.ps1.'
