# Device validation — 2026-10-23 (JST)

## Environment

Baseline remote/local HEAD: `1da9d397663e947894b305941e95cc72fc810cec`.
Branch: `codex/device-test-harness`.
USB ADB reports `device`; model `Titan 2`, product/device `Titan_2`, Android 16. These values do not independently verify the user's Elite variant designation.
Java 17.0.20.1, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, ADB 37.0.1. SDK 35 was installed using an existing accepted SDK license. SDK XML warning was non-fatal.

## Results and fixes

- Composer instrumentation initially failed: `konnichiha` produced `こんいちは`. Deferred nn resolution fixed this; greeting, doubled consonant, final n and nn/nna/nnka/kanna regressions pass on the device.
- Ctrl+Space changed the default Android IME. Reproduced in an input-only run, excluding reinstall as a necessary cause. The per-key selection guard stopped further input. Replaced internal toggle with Ctrl+Shift+J.
- Input tests now run in separate package `dev.utmity.titanime.harness`, avoiding instrumentation of the selected IME's package. Tests wait for focused editor and the Debug IME onStartInput notification, explicitly request IME input and allow connection delivery to settle. Soft IME visibility alone was not a valid readiness condition on this hardware-keyboard device.
- A final standalone harness run passed English `a`, deletion, EN→JA, pending `k`, deleting the last composing character, `ka`→`か` with composing span, Enter→`か\n` with no composing span, pending `n`, JA→EN committing `ん`, and English `b`. IME diagnostic logs confirmed key delivery and both mode changes.
- Backspace now replaces composing text with empty text before finishing composition. The last-character deletion assertion passed through the actual InputConnection.

These are injected Android key events on the physical device, not physical keyboard presses. No claim is made about arbitrary editors, passwords, cursor movement, Japanese conversion candidates, or all romaji spellings.

## IME settings

Initial default: `com.iqqijni.bbkeyboard/.keyboard_service.view.HDKeyboardService`.
PhysiBoard dev3, this service and FluxKeyboard were enabled initially. Titan IME was registered by APK installation, then enabled/selected manually by the user.
The user subsequently authorized automated default-IME changes. `-SelectTitanIme` now explicitly selects the already-enabled Titan IME and restores the selection recorded at run start in a finally block. No enable/disable/uninstall commands or USB-debugging setting changes are used. Current test baseline is Titan IME itself.

## Hardware evidence

`getevent -lp` reports `/dev/input/event5` named `TitanKey`; event numbers may change after reboot. Advertised Linux capabilities include letters, BACKSPACE, ENTER, RIGHTSHIFT, LEFTALT, SPACE, F1/F10/F11/F13, arrows, vendor codes `00fb`/`00fd`. These are capabilities, not observed mappings for labeled physical keys.

Injected events observed by the IME: A=29, J=38, K=39, N=42, B=30, DEL=67, ENTER=66; Ctrl+Shift meta=4097. ScanCode=0/device=-1 identifies these as synthetic, not hardware evidence.

Sym/Fn and physical scan-code mapping remain pending labeled user presses. An initial getevent observation window contained no presses and was stopped. The probe logs key metadata, not editor contents; metadata can still reveal keys, so use only test input. IME-side probe logging is restricted to debuggable builds and the harness editor package. The harness activity probe is confined to that app.

## Reproduce

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\scripts\test-device.ps1 -Serial <serial> -InputSequence -SelectTitanIme -FallbackIme <enabled-other-ime-id>
# Existing APKs only:
.\scripts\test-device.ps1 -Serial <serial> -InputSequence -SelectTitanIme -FallbackIme <enabled-other-ime-id> -SkipInstall
```

Start in English mode with screen unlocked. A successful sequence returns to English; a failed sequence may leave internal mode in Japanese. The run saves build output, JVM composer test build output/input results, key capabilities and filtered logs in ignored `test-results/`. Raw serials and other-app logs are not committed.

Open the physical-key test editor with:
`adb shell am start -n dev.utmity.titanime.harness/dev.utmity.titanime.InputTestActivity`.
Correlate labeled presses with `adb shell getevent -lt` and `TitanKeyProbe`/`TitanImeProbe` logs. System-consumed keys may not reach the activity. A previous unrelated WebView crash in logcat was not attributed to Titan IME.

## Final pipeline result

PASS: the final runner built the APKs, passed the JVM composer regressions, temporarily selected the already-enabled original keyboard service, installed the IME/harness/harness-test APKs, selected Titan, received its input-start notification, and passed `OK (1 test)` for the full input sequence. It restored the run-start selection (Titan) without an enabled/default mismatch warning.

Composer tests were initially executed on the device to reproduce the bug, then moved to JVM tests so running them no longer instruments/restarts the IME package. Device input tests remain separate and exercise the real service and InputConnection.

On this device, reinstalling while Titan remained selected sometimes left its service disconnected despite the selection setting. An explicit `-FallbackIme` before installation resolved this in the final end-to-end run. The fallback must already be enabled and requires `-SelectTitanIme`; the script never chooses an unknown fallback itself. One stalled Android package-install session was explicitly abandoned and retried successfully; no package or security setting was removed/disabled.

## 2026-10-24 resume

USB device remained connected. Titan was installed/registered but absent from enabled_input_methods, and `ime set` returned `Unknown input method`. After the user's authorization to manage default-IME switching, Titan alone was re-enabled and selected for continued testing. Other enabled IMEs were retained. The runner still does not enable/disable IMEs automatically. Physical TitanKey capture was started for labeled user presses; this is not yet a mapping result.
