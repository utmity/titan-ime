param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [switch]$InputSequence
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Adb {
    $result = & adb.exe -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
if ((Adb get-state).Trim() -ne 'device') { throw 'Device is not ready' }
$before = (Adb shell settings get secure default_input_method) -join "`n"
$enabled = (Adb shell settings get secure enabled_input_methods) -join "`n"
if ($InputSequence -and $before.Trim() -ne 'dev.utmity.titanime/.TitanImeService') {
    throw 'Select Titan IME manually on the device first. No IME settings were changed.'
}
$output = Join-Path 'test-results' (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force $output | Out-Null
& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain 2>&1 | Tee-Object "$output/build.log"
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
Adb install -r app/build/outputs/apk/debug/app-debug.apk
Adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
try {
    Adb shell ime list -a -s | Set-Content "$output/ime-registration.txt"
    Adb shell getevent -lp | Set-Content "$output/input-capabilities.txt"
    $test = 'dev.utmity.titanime.InputDeviceTest#testComposer'
    if ($InputSequence) { $test = 'dev.utmity.titanime.InputDeviceTest' }
    $result = Adb shell am instrument -w -e class $test dev.utmity.titanime.test/androidx.test.runner.AndroidJUnitRunner
    $result | Tee-Object "$output/instrumentation.txt"
    Adb logcat -d -s TitanKeyProbe:I AndroidRuntime:E '*:S' | Set-Content "$output/logcat.txt"
    if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Instrumentation did not pass; inspect results' }
} finally {
    $after = (Adb shell settings get secure default_input_method) -join "`n"
    $afterEnabled = (Adb shell settings get secure enabled_input_methods) -join "`n"
    if ($before -ne $after -or $enabled -ne $afterEnabled) {
        Write-Warning 'IME settings changed during the run. Inspect manually; no automatic restoration is attempted.'
    }
}
