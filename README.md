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

Status: installable prototype with a separate test-editor app and device instrumentation tests.

## Device development (Windows)
Set `ANDROID_HOME` to the Android SDK directory and use JDK 17.
The Gradle 8.9 wrapper builds the IME (`app`) and separate editor (`harness`).
Run `./scripts/test-device.ps1 -Serial <adb-serial>` for build, install and composer checks.
Add `-InputSequence -SelectTitanIme` to explicitly select an already enabled Titan IME for the test and restore the previous selection in finally. Without `-SelectTitanIme`, select Titan IME manually.
The script never enables/disables IMEs or uninstalls packages. `-SkipInstall` tests installed APKs without building or installing.
Results are saved under ignored `test-results/`; inspect before sharing logs.

Open the isolated editor with `adb -s <adb-serial> shell am start -n dev.utmity.titanime.harness/dev.utmity.titanime.InputTestActivity`.
Its key probe records action, keyCode, scanCode, metaState and deviceId, not editor text.
Keys consumed by an IME or Android may not reach the activity; absent events do not prove absent hardware keys.
`adb shell getevent -lt` can supplement the probe while manually pressing known keys; stop with Ctrl+C.
Injected key events test Android input routing, not physical Sym/Fn mappings.
See [device validation](docs/device-validation.md) for observed results and remaining checks.

Language toggle: Ctrl+Shift+J. Ctrl+Space switches Android IMEs on the tested device and must not be used for the internal toggle.

On the tested device, use -FallbackIme <enabled-other-ime-id> with -SelectTitanIme to switch away before reinstalling and reconnect Titan afterwards. The original selection is restored even on test failure. Composer regressions run as JVM unit tests; input routing runs in the separate harness app. The Debug IME sends an input-start notification only to that harness so tests can wait for an actual connection.

Japanese mode passes Sym/function-modified keys through after committing pending text. Titan's observed Fn key (403 / scan 251) is tracked while held because Fn+A carries no standard modifier bit; its state is cleared on release and input lifecycle changes. No symbol mapping is supplied.

Pre-review checks: run Gradle tasks :app:lintDebug :harness:lintDebug :app:assembleRelease and scripts/test-runner-cleanup.ps1. The cleanup test uses a mocked ADB function and verifies IME restoration when log collection fails. Release output is unsigned; it is not a distribution-ready package.

GitHub Actions runs unit tests, Android Lint, Debug/unsigned Release builds, and the offline Windows cleanup regression on pull requests and main pushes. Reports and Debug test APKs are retained for 7 days. CI does not run a Titan device or validate physical key behavior; use scripts/test-device.ps1 for that. CI Debug APKs use a different signing key from local builds, so use locally built APKs for adb install -r over an existing local installation.
