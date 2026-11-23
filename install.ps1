$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Test-Path -LiteralPath '.venv\Scripts\python.exe')) {
    python -m venv .venv
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.11+ is required. Install Python and enable its PATH option.' }
}
& '.\.venv\Scripts\python.exe' -m pip install -r requirements-lock.txt
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Check internet access and Python version.' }
& '.\.venv\Scripts\python.exe' -c "from iskan.imaging import OCR; import numpy as np; OCR()(np.full((200,200,3),255,dtype=np.uint8)); print('OFFLINE_OCR_READY')"
if ($LASTEXITCODE -ne 0) { throw 'OCR self-check failed.' }
Write-Host 'I-SKAN installed. Use setup-https.ps1 for phone capture or start.ps1 -Local for laptop use.'
