param([switch]$Local, [switch]$HttpLan, [int]$Port = 8443, [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath '.venv\Scripts\python.exe')) { throw 'Run install.ps1 first.' }
$scanArgs = @('launch.py', '--port', "$Port")
if ($Local) { $scanArgs += '--local' }
if ($HttpLan) { $scanArgs += '--http-lan' }
if ($NoBrowser) { $scanArgs += '--no-browser' }
& '.\.venv\Scripts\python.exe' @scanArgs
