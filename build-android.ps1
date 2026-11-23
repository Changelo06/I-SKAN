param([switch]$Check)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$scanJbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
if (Test-Path -LiteralPath (Join-Path $scanJbr 'bin\java.exe')) { $env:JAVA_HOME = $scanJbr }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath $env:ANDROID_HOME)) { throw 'Install Android SDK platform 35 and set ANDROID_HOME, or open android/ in Android Studio.' }
$scanTasks = @('assembleDebug')
if ($Check) { $scanTasks += @('testDebugUnitTest','assembleDebugAndroidTest','lintDebug') }
& '.\android\gradlew.bat' -p android @scanTasks --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build/check failed.' }
$scanOutput = Join-Path $PSScriptRoot 'output\android'
New-Item -ItemType Directory -Path $scanOutput -Force | Out-Null
Copy-Item -LiteralPath 'android\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk' -Destination (Join-Path $scanOutput 'I-SKAN-Android-arm64-v2.2.0.apk') -Force
Copy-Item -LiteralPath 'android\app\build\outputs\apk\debug\app-x86_64-debug.apk' -Destination (Join-Path $scanOutput 'I-SKAN-Android-x86_64-v2.2.0.apk') -Force
Get-FileHash -LiteralPath (Join-Path $scanOutput 'I-SKAN-Android-arm64-v2.2.0.apk') -Algorithm SHA256
Write-Host 'APK ready in output/android. Install the arm64 build on an Android 10+ phone.'

