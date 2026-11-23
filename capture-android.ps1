param([string]$Serial)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$scanSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
$scanAdb = Join-Path $scanSdk 'platform-tools/adb.exe'
$scanJbr = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
if (Test-Path -LiteralPath (Join-Path $scanJbr 'bin/java.exe')) { $env:JAVA_HOME = $scanJbr }
if (-not $Serial) {
    $scanConnected = @(& $scanAdb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($scanConnected.Count -ne 1) { throw 'Connect exactly one authorized Android device, or pass -Serial.' }
    $Serial = $scanConnected[0]
}
$scanState = (& $scanAdb -s $Serial get-state 2>$null | Out-String).Trim()
if ($scanState -ne 'device') { throw 'Android device is not connected and authorized.' }
$scanBoot = (& $scanAdb -s $Serial shell getprop sys.boot_completed | Out-String).Trim()
if ($scanBoot -ne '1') { throw 'Wait for Android to finish booting, then rerun.' }
$scanAbi = (& $scanAdb -s $Serial shell getprop ro.product.cpu.abi | Out-String).Trim()
if ($scanAbi -notin @('arm64-v8a','x86_64')) { throw 'This preview requires arm64-v8a or x86_64.' }
$scanEmulated = ((& $scanAdb -s $Serial shell getprop ro.kernel.qemu | Out-String).Trim() -eq '1')
if ($scanEmulated) {
    & $scanAdb -s $Serial shell wm size 1080x1920
    & $scanAdb -s $Serial shell wm density 420
    & $scanAdb -s $Serial shell cmd uimode night no
}
& './android/gradlew.bat' -p android assemblePreview assemblePreviewAndroidTest -PscreenshotBuild --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Preview build failed.' }
$scanApk = "android/app/build/outputs/apk/preview/app-$scanAbi-preview.apk"
$scanTestApk = 'android/app/build/outputs/apk/androidTest/preview/app-preview-androidTest.apk'
& $scanAdb -s $Serial install -r -t $scanApk
if ($LASTEXITCODE -ne 0) { throw 'Preview APK installation failed.' }
& $scanAdb -s $Serial install -r -t $scanTestApk
if ($LASTEXITCODE -ne 0) { throw 'Screenshot test installation failed.' }
$scanCamera = if ($scanEmulated) { 'true' } else { 'false' }
$scanResult = & $scanAdb -s $Serial shell am instrument -w -r -e class org.iskan.mobile.AppScreenshotsTest -e captureCamera $scanCamera org.iskan.mobile.preview.test/androidx.test.runner.AndroidJUnitRunner
$scanResult | Write-Output
if (($scanResult | Out-String) -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=') { throw 'Native screenshot capture did not finish successfully.' }
$scanOutput = Join-Path $PSScriptRoot ('output/screenshots/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $scanOutput -Force | Out-Null
& $scanAdb -s $Serial pull /sdcard/Android/data/org.iskan.mobile.preview/files/screenshots/. $scanOutput
if ($LASTEXITCODE -ne 0) { throw 'Could not pull the Android screenshots.' }
$scanFiles = @(Get-ChildItem -LiteralPath $scanOutput -Filter '*.png')
if ($scanFiles.Count -lt 6) { throw 'The screenshot set is incomplete.' }
@{ kind='Actual Android window captures'; package='org.iskan.mobile.preview'; device=$Serial; sample_documents=$true; camera_captured=$scanEmulated; images=@($scanFiles.Name) } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $scanOutput 'CAPTURE-INFO.json') -Encoding utf8
Write-Host "Real app screenshots saved to $scanOutput"
