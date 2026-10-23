# Titan IME

Physical-keyboard-first Android IME for Unihertz Titan 2 Elite.

## Goals
- Keep English key input on a minimal direct-commit path.
- Add Japanese romaji composition without switching Android IMEs.
- Preserve hardware Sym/Fn handling and allow configurable language toggle.
- Integrate a Japanese conversion engine later behind a non-blocking interface.
- Keep a distinct application ID so existing IMEs remain installed.

## Development stages
1. Independent Android InputMethodService and testable Kotlin romaji composer.
2. Composition editing, cursor, cancellation, hardware key routing and performance tests.
3. Mozc integration, candidate UI, dictionary and learning.

## Reference
PhysiBoard (https://github.com/brobata/physiboard) is a GPL-3.0 clean-room rewrite with pure Kotlin core modules. Any reuse of its source requires license compliance; this project currently contains no PhysiBoard code.

Status: installable prototype with a Debug-only editor and device instrumentation tests.

## Device development (Windows)
Set `ANDROID_HOME` to the Android SDK directory and use JDK 17.
`./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` uses Gradle 8.9.
Run `./scripts/test-device.ps1 -Serial <adb-serial>` for build, install and composer checks.
Add `-InputSequence` only after manually enabling and selecting Titan IME on the device.
The script never enables, disables or selects an IME. It never uninstalls packages.
Results are saved under ignored `test-results/`; inspect before sharing logs.

Open the isolated editor with `adb -s <adb-serial> shell am start -n dev.utmity.titanime/.InputTestActivity`.
Its Debug-only key probe records action, keyCode, scanCode, metaState and deviceId, not editor text.
Keys consumed by an IME or Android may not reach the activity; absent events do not prove absent hardware keys.
`adb shell getevent -lt` can supplement the probe while manually pressing known keys; stop with Ctrl+C.
Injected key events test Android input routing, not physical Sym/Fn mappings.
See [device validation](docs/device-validation.md) for observed results and remaining checks.

Language toggle: Ctrl+Shift+J. Ctrl+Space switches Android IMEs on the tested device and must not be used for the internal toggle.
