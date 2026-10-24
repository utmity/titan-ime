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

The initial observation window contained no presses; the labeled physical capture below subsequently completed the requested mapping. The probe logs key metadata, not editor contents; metadata can still reveal keys, so use only test input. IME-side probe logging is restricted to debuggable builds and the harness editor package. The harness activity probe is confined to that app.

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

## Physical key capture completed — 2026-10-24

The user confirmed pressing A, Sym, Fn, Sym+A, Fn+A in that order. Linux events from TitanKey and Android activity/IME logs matched all five groups. Device log timestamps were 05:36:06–05:36:24 JST; Linux monotonic timestamps were 39507.460946–39525.585390. These are physical events, deviceId=0 (not the earlier injected deviceId=-1).

| Physical operation | Linux EV_KEY | Android keyCode / scanCode | Observed metaState |
| --- | --- | --- | --- |
| A | KEY_A | 29 / 30 | 0 on down/up |
| Sym | 0x00fd (253) | 63 (KEYCODE_SYM) / 253 | down=4, up=0 |
| Fn | 0x00fb (251) | 403 / 251 | 0 on down/up |
| Sym held + A | 0x00fd down, KEY_A down/up, 0x00fd up | A=29 / 30 | A down/up=4 |
| Fn held + A | 0x00fb down, KEY_A down/up, 0x00fb up | A=29 / 30 | A down/up=0 |

SDK definitions identify meta=4 as META_SYM_ON; META_FUNCTION_ON is 8 and was not present in the Fn+A observation. The local SDK KeyEvent definitions do not name code 403; retain the numeric value rather than guessing a standard Fn key code.

All down events reached TitanImeService with japanese=false and connected=true, and both down/up events reached the harness activity. This verifies English-mode pass-through for this sequence. It does not establish Japanese-mode Sym/Fn semantics, the produced characters, other Fn chords, long presses or repeat behavior. Future Fn support needs the observed key's own press/release state (or further device mapping evidence), not only META_FUNCTION_ON. No Fn remapping is introduced by this PR.

Capture was stopped after collecting the sequence. Default IME remains Titan; previously enabled PhysiBoard, the original keyboard service and FluxKeyboard remain enabled.

## Japanese modifier routing follow-up — 2026-10-24

A new synthetic regression on the real device reproduced Sym+A being converted to あ in Japanese mode. The expected policy is to pass modified keys through, not feed them into romaji composition. The test uses META_SYM_ON and the previously observed vendor Fn keyCode=403/scanCode=251 down/A/up sequence; it is not a second physical capture.

The IME now bypasses romaji for Sym, standard META_FUNCTION_ON, and a held vendor Fn identified by that exact key/scan pair. Vendor Fn state is tracked per device ID, released on key-up and cleared on input lifecycle changes. No symbol/numeric key mapping is invented; events are forwarded to the editor/system.

Pending composition is committed before modified input. An expanded device test also exposed a cursor/order race when commitText was followed by normal key fallthrough: subsequent text was inserted before the modified letter. Routing that key through the same InputConnection after committing fixed the observed sequence.

Final build/install/select/test/restore pipeline: PASS, `OK (1 test)`, 6.420 seconds. The existing input sequence plus Japanese Sym+A, vendor Fn+A, Fn-release recovery, pending n followed by Sym+A, pending k followed by Fn+A, and standard function-modified A all passed. Composer JVM regressions passed as part of the build. The run restored its initial default, PhysiBoard dev3.

Physical Japanese-mode Sym/Fn presses and other editors remain unverified. The earlier physical capture established the mapping in English mode only. A lost Fn-up without an input lifecycle transition is not separately tested.

## Japanese-mode physical verification — 2026-10-24

PASS on commit a3a9d0e: the user physically pressed Sym+A, released both keys, pressed Fn+A, released both keys, then K, A, Enter. The harness was empty and the IME diagnostic probe confirmed japanese=true before and throughout this sequence.

Linux TitanKey timestamps 40629.849152–40648.563908 and Android logs 05:54:48–05:55:07 JST matched all requested operations. Sym+A reached the editor with keyCode=29, scanCode=30, meta=4; Fn+A reached it with the same key/scan and meta=0, following vendor Fn=403/251. DeviceId=0 identifies physical events. K used scanCode=37; Enter used scanCode=28. Normal K/A down events were consumed by the Japanese composer after Fn release.

Reading only the test EditText through UI Automator after Enter returned `aaか\n`. This confirms actual physical Japanese-mode Sym/Fn pass-through, release recovery into romaji conversion, and Enter commitment/newline in this editor. The preparation mode toggle and initial empty-field check were injected events, separate from the user-operated sequence.

Capture stopped; default IME restored to the pre-test PhysiBoard dev3. No source change was needed after this physical test. Pending-composition-plus-modifier cases remain covered by injection regressions; they were not repeated physically. Other editors, other Fn chords and lost-key-up behavior remain outside this verification.

## PR finishing checks — 2026-10-24

- `:app:lintDebug :harness:lintDebug :app:assembleRelease`: PASS after replacing the test receiver registration branches with ContextCompat.registerReceiver and an explicit exported flag (the sender is the separate IME app).
- Lint warnings remain for prototype metadata/backup rules, available dependency updates and diagnostic UI text; no lint errors. Release output is `app-release-unsigned.apk`: build-verified only, not signed for distribution or installed on the device.
- Fixed runner cleanup: a failed logcat collection now emits a warning instead of skipping IME restoration. Offline `scripts/test-runner-cleanup.ps1` mocks ADB and verifies restoration with both successful and failed instrumentation while logcat fails; PASS. It does not access a device.
- Rebuilt/reinstalled and reran the complete device regression after the receiver change: `OK (1 test)`, 6.357 seconds. The runner restored PhysiBoard dev3 successfully.

## Punctuation ordering regression — 2026-10-24

Device-injected Japanese `n`, period, `k`, `a` reproduced `んか.` instead of `ん.か`. The committed text initially looked correct after the period, but the next composing syllable exposed a stale insertion position. The ordinary printable-symbol path now sends the key through the same InputConnection after committing pending text, matching the ordered modifier path.

Regression cases cover period, comma and digit 1 after pending n, followed by ka and Enter. This tests actual Android editor/IME interaction on the USB device using injected events; these punctuation sequences were not physically pressed by the user.

Punctuation fix validation completed: full device regression PASS, `OK (1 test)`, 8.875 seconds. Period/comma/digit followed by ka and Enter passed together with all earlier input and modifier cases. `:app:lintDebug :harness:lintDebug :app:assembleRelease` also passed; release APK remains unsigned and was not installed. The resumed run recorded Titan as its initial selection and restored Titan at completion.

An installation pause was caused by Play Protect asking whether to send Titan IME Test to Google for analysis. The user handled this prompt; the agent did not choose upload consent or change Play Protect settings. The earlier interrupted run had saved only build output and was not counted as a successful device test.
