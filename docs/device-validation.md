# Device validation — 2026-10-23 (JST)

## Baseline and environment

- Remote `origin/main` and initial local HEAD: `1da9d397663e947894b305941e95cc72fc810cec`.
- Work branch: `codex/device-test-harness`.
- USB ADB reports `device`; Android reports model `Titan 2`, product/device `Titan_2`, Android 16. The user's name for the device is T2E; the reported model alone does not verify an Elite variant.
- Java: Microsoft OpenJDK 17.0.20.1; Gradle: 8.9; Android Gradle Plugin: 8.7.3; Kotlin: 2.0.21.
- ADB: 37.0.1. SDK platform 35 was missing and installed by Gradle using the already accepted SDK license.
- ADB/network initially failed in the sandbox; the same operations succeeded with approved host execution.
- SDK XML version warning was non-fatal. Both APK builds completed successfully.

## IME preservation

Initial/default IME: `com.iqqijni.bbkeyboard/.keyboard_service.view.HDKeyboardService`.
Enabled entries initially included PhysiBoard dev3, that keyboard service, and FluxKeyboard.
Titan IME was absent before installation and present in `ime list -a -s` afterwards.
No IME was enabled, disabled, selected or uninstalled by this work. No USB debugging settings changed.
The default remained unchanged after installation and the composer test.

## Evidence and scope

| Check | Observed result |
| --- | --- |
| Debug APK + instrumentation APK build/install | Successful, including rebuild/reinstall after fixes |
| Composer instrumentation on physical device, before fix | FAIL: `konnichiha` expected `こんにちは`, actual `こんいちは` |
| Composer instrumentation on physical device, after fix | `OK (1 test)`; greeting, doubled consonant, final n, nn/nna/nnka/kanna cases |
| English, Japanese composition, Backspace, Enter, Ctrl+Space through selected IME | Pending user selection of Titan IME; test implemented, not claimed passed |
| Delete final composing character | Code inspection found finishComposingText alone leaves text behind; changed to replace with empty composing text first. Real IME regression remains pending |
| Sym/Fn physical presses | Pending labeled manual presses; no mapping inferred from injected events |

The composer test runs Kotlin logic inside Android instrumentation on the actual device. It does **not** exercise InputMethodService or the editor connection.
The input sequence must start with Titan IME in English mode, an unlocked screen, and the test editor focused. Its successful path returns to English. A failed run may leave Japanese mode active; check/reset with Ctrl+Space manually before retrying.
Ctrl+Space can be intercepted by Android. Failure here must be investigated rather than treated as proof of a composer defect.

## Hardware observations

`adb shell getevent -lp` identified `/dev/input/event5` named `TitanKey`.
Advertised Linux keys include letters, BACKSPACE, ENTER, RIGHTSHIFT, LEFTALT, SPACE, F1, F10, F11, F13, arrows and vendor codes `00fb`/`00fd`.
These are capability declarations, **not observed press mappings**; event numbers may change after reboot.
Other devices include `touchPad`, `fts_ts`, `ff_key` and `mtk-pmic-keys`.
The Debug editor displays/logs Android keyCode, scanCode, metaState, action, repeat count and device ID. IME/system-consumed events may not reach it.
For Sym/Fn, correlate a labeled physical sequence with `getevent -lt` and the probe log. Do not substitute `adb input keyevent` for hardware evidence.

Logcat inspection contained an older WebView sandbox crash from another package; no causal connection to Titan IME was established. No TitanKeyProbe presses had been recorded at that inspection.

## Reproduce

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\scripts\test-device.ps1 -Serial <serial>
# Manually enable/select Titan IME, start in English mode, then:
.\scripts\test-device.ps1 -Serial <serial> -InputSequence
```

The script checks the selected IME before input injection and fails without switching it. It writes build output, instrumentation output, capability metadata and filtered logs to ignored `test-results/`. Raw device serials and other-app logs are deliberately not committed here.

Automation verification: the PowerShell runner completed build/install/composer/log collection successfully. Running with -InputSequence while the original IME remained selected exited before build or input injection, with the expected manual-selection message.
