param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [switch]$InputSequence,
    [switch]$SkipInstall,
    [switch]$SelectTitanIme,
    [string]$FallbackIme
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Adb {
    $result = & adb.exe -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
$titan = 'dev.utmity.titanime/.TitanImeService'
if (((Adb get-state) -join "`n").Trim() -ne 'device') { throw 'Device is not ready' }
$before = ((Adb shell settings get secure default_input_method) -join '').Trim()
$enabled = (Adb shell settings get secure enabled_input_methods) -join "`n"
if ($FallbackIme -and -not $SelectTitanIme) { throw '-FallbackIme requires -SelectTitanIme' }
if ($FallbackIme -and $FallbackIme -notin ($enabled.Trim().Split(':') | ForEach-Object { $_.Split(';')[0] })) {
    throw 'Fallback IME must already be enabled'
}
if ($InputSequence -and -not $SelectTitanIme -and $before -ne $titan) {
    throw 'Select Titan IME manually or explicitly pass -SelectTitanIme.'
}
$output = Join-Path 'test-results' (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force $output | Out-Null
if (-not $SkipInstall) {
    & .\gradlew.bat :app:assembleDebug :harness:assembleDebug :harness:assembleDebugAndroidTest --console=plain 2>&1 | Tee-Object "$output/build.log"
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
}
function RunTest([string]$Package, [string]$Name) {
    $result = Adb shell am instrument -w -e class dev.utmity.titanime.InputDeviceTest "$Package/androidx.test.runner.AndroidJUnitRunner"
    $result | Tee-Object "$output/$Name.txt"
    if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw "Instrumentation failed: $Name" }
}
try {
    if ($FallbackIme) { Adb shell ime set $FallbackIme }
    if (-not $SkipInstall) {
        Adb install -r app/build/outputs/apk/debug/app-debug.apk
        Adb install -r harness/build/outputs/apk/debug/harness-debug.apk
        Adb install -r harness/build/outputs/apk/androidTest/debug/harness-debug-androidTest.apk
    }
    if ($SelectTitanIme) { Adb shell ime set $titan }
    Adb shell ime list -a -s | Set-Content "$output/ime-registration.txt"
    Adb shell getevent -lp | Set-Content "$output/input-capabilities.txt"
    if ($InputSequence) {
        if (((Adb shell settings get secure default_input_method) -join '').Trim() -ne $titan) {
            throw 'Titan IME is no longer selected. Reselect manually or use -SelectTitanIme.'
        }
        RunTest 'dev.utmity.titanime.harness.test' 'input-sequence'
    }
} finally {
    try {
        Adb logcat -d -s TitanKeyProbe:I TitanImeProbe:I AndroidRuntime:E '*:S' | Set-Content "$output/logcat.txt"
    } catch {
        Write-Warning "Could not collect logcat: $_"
    }
    if ($SelectTitanIme) { Adb shell ime set $before }
    $after = ((Adb shell settings get secure default_input_method) -join '').Trim()
    $afterEnabled = (Adb shell settings get secure enabled_input_methods) -join "`n"
    if ($before -ne $after -or $enabled -ne $afterEnabled) {
        Write-Warning 'IME settings differ from the baseline; inspect the device.'
    }
}
