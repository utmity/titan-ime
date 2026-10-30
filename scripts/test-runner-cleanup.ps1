# Offline failure-path regression. No ADB executable or device is used.
$ErrorActionPreference = 'Stop'
if (Test-Path Function:\adb.exe) { throw 'Run in a fresh PowerShell session' }
$initialLocation = Get-Location
function global:adb.exe {
    $global:LASTEXITCODE = 0
    $command = ($args | Select-Object -Skip 2) -join ' '
    switch -Regex ($command) {
        '^get-state$' { return 'device' }
        '^shell settings get secure default_input_method$' { return $global:titanRunnerMock.selected }
        '^shell settings get secure enabled_input_methods$' { return 'example.original/.Ime:dev.utmity.titanime/.TitanImeService' }
        '^shell ime set (.+)$' {
            $global:titanRunnerMock.selected = $Matches[1]
            $global:titanRunnerMock.selections.Add($Matches[1])
            return 'selected'
        }
        '^shell ime list' { return 'dev.utmity.titanime/.TitanImeService' }
        '^shell getevent' { return 'mock capabilities' }
        '^shell am start -W -n dev\.utmity\.titanime\.harness/dev\.utmity\.titanime\.InputTestActivity$' { return 'Status: ok' }
        '^shell am instrument' {
            if ($global:titanRunnerMock.failTest) { return 'FAILURES!!!' }
            return 'OK (1 test)'
        }
        '^logcat' {
            $global:LASTEXITCODE = 1
            return 'simulated logcat failure'
        }
        default { throw "Unexpected mock command: $command" }
    }
}
try {
    foreach ($failTest in @($false, $true)) {
        $global:titanRunnerMock = @{
            selected = 'example.original/.Ime'
            selections = [System.Collections.Generic.List[string]]::new()
            failTest = $failTest
        }
        $failure = $null
        try {
            & "$PSScriptRoot/test-device.ps1" -Serial mock -SkipInstall -InputSequence -SelectTitanIme | Out-Null
        } catch { $failure = $_.Exception.Message }
        if ($global:titanRunnerMock.selected -ne 'example.original/.Ime' -or
            $global:titanRunnerMock.selections.Count -ne 2) {
            throw 'Original IME was not restored after logcat failed'
        }
        if ($failTest -and $failure -notlike '*Instrumentation failed: input-sequence*') {
            throw "Instrumentation failure was lost: $failure"
        }
        if (-not $failTest -and $failure) { throw $failure }
    }
    Write-Output 'PASS: restoration survives logcat failure with passing and failing instrumentation.'
} finally {
    Remove-Item Function:\adb.exe
    Remove-Variable titanRunnerMock -Scope Global -ErrorAction SilentlyContinue
    Set-Location $initialLocation
}
