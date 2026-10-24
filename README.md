# Titan IME

Physical-keyboard-first Android IME for Unihertz Titan 2 Elite, with on-device Mozc kana-kanji conversion.

## Priorities and architecture

Mozc owns Japanese romaji, composition, conversion and candidates. Titan owns Android editor updates and physical-key routing. The custom Kotlin romaji converter has been removed; its regression cases now run against Mozc on the device.

Basic kana-kanji conversion and editor stability take priority over settings and additional shortcuts. See [integration decisions, OSS licenses and build requirements](docs/mozc-integration.md) and [device validation](docs/device-validation.md).

## Input

- Ctrl+Shift+J toggles English/Japanese. Ctrl+Space is reserved by Android for IME switching on the tested device.
- Type romaji, press Space to convert, then Space again to advance candidates. The candidate strip also supports touch selection.
- Enter commits composing/converting text without sending an Enter to the editor. Press Enter again for the editor's normal action.
- During conversion, Left/Right or the 前文節/次文節 strip controls move segment focus; Shift+Left/Right changes segment length. The focused segment is highlighted.
- Moving the editor cursor finalizes the visible composition and starts subsequent input at the new selection. Before conversion, Left/Right commits the reading and moves the editor cursor.
- Escape returns from conversion to the reading; Backspace edits through Mozc.
- Sym/function-modified keys pass through after committing pending text. The observed Titan Fn pair (key 403 / scan 251) is tracked per device because Fn+A has no standard modifier flag. No symbol remapping is supplied.
- Password and non-text fields use direct input. Learning is disabled for this initial integration. The app has no network permission.

## Build and device tests (Windows)

Use JDK 17, Android SDK 35, and set ANDROID_HOME. The Gradle 8.9 wrapper builds the IME (`app`) and separate editor (`harness`).

First build the pinned engine on Linux/WSL2 with `bash scripts/build-mozc.sh`, or download the mozc-arm64 artifact from a successful trusted GitHub Actions run in this repository. Extract it and run `./scripts/import-mozc.ps1 -Source <extracted-directory>`. The importer checks revision and checksums. Engine and dictionary binaries are ignored by Git; Gradle fails clearly if they are missing. Initial device support is arm64-v8a only.

Run `./scripts/test-device.ps1 -Serial <adb-serial> -InputSequence -SelectTitanIme` to build, install, test and restore the starting IME. Titan must already be enabled. Without -SelectTitanIme, select Titan manually. The runner never enables/disables IMEs or uninstalls packages. Use -SkipInstall to test installed APKs.

On this device, add `-FallbackIme <enabled-other-ime-id>` with -SelectTitanIme to switch away before updating Titan, avoiding the observed stale connection after installation. The original selection is restored in finally, even if instrumentation or log collection fails.

Results are saved under ignored test-results/. Inspect before sharing logs. Composer/conversion regressions run through the actual native engine in the separate harness instrumentation; there is no mock converter or old JVM composer test counted as Mozc validation.

Open the isolated editor with `adb -s <adb-serial> shell am start -n dev.utmity.titanime.harness/dev.utmity.titanime.InputTestActivity`. Its key probe records action, keyCode, scanCode, metaState and deviceId. A Debug-only IME notification lets tests wait for an actual input connection. Injected events test Android input routing, not physical hardware signals. Use `adb shell getevent -lt` while pressing known keys for physical capture, then stop capture.

## CI and review

GitHub Actions builds/caches the pinned Mozc engine and dictionary on Linux, packages Debug and unsigned Release APKs, runs Android Lint and the offline Windows cleanup regression. The latter uses mocked ADB to verify restoration after test/log failures. CI has no Titan device; use the device runner for native input tests.

Reports and Debug test APKs are retained for 7 days. CI Debug signing differs from local builds: use locally built APKs for install-r over an existing local installation. Release output is unsigned and is not distribution-ready.

Local checks: `./gradlew.bat :app:lintDebug :harness:lintDebug :app:assembleRelease` and `./scripts/test-runner-cleanup.ps1`.
