# Titan IME

Physical-keyboard-first Android IME for Unihertz Titan 2 Elite, with on-device Mozc kana-kanji conversion.

## Priorities and architecture

Mozc owns Japanese romaji, composition, conversion and candidates. Titan owns Android editor updates and physical-key routing. The custom Kotlin romaji converter has been removed; its regression cases now run against Mozc on the device.

Basic kana-kanji conversion and editor stability take priority over settings and additional shortcuts. See [integration decisions, OSS licenses and build requirements](docs/mozc-integration.md) and [device validation](docs/device-validation.md).

## 操作一覧（実装済み）

| 機能 | 操作 | 動作・条件 |
| --- | --- | --- |
| 日英切り替え | Ctrl+Shift+J | Ctrl+SpaceはAndroidのIME切り替え用 |
| 日本語入力 | ローマ字 | Mozcでかな入力。独自変換器は使用しない |
| 記号 | `-` / `,` / `.` | 日本語モードで `ー` / `、` / `。`。未確定のままMozcへ入力 |
| 括弧 | `[` / `]` | 日本語モードで `「` / `」`。その他の引用符・記号もMozcへ渡す |
| 漢字変換・候補送り | Space | 入力中は変換、変換中は次候補 |
| 候補選択 | 候補をタップ | 選択後Enterで確定 |
| 前／次文節 | Fn+H / Fn+L | **変換中のみ**。選択文節を青背景で表示 |
| 文節を縮める／伸ばす | Fn+Shift+H / Fn+Shift+L | **FnとShiftを先に押してからH/L**。変換中のみ |
| 文節のタップ移動 | 前文節 / 次文節 | 候補欄のボタン |
| 外付け矢印キー | 左右 / Shift+左右 | 文節移動／文節長変更。T2Eには独立した矢印キーなし |
| 確定 | Enter | 未確定文字だけを確定。もう一度Enterで改行・編集欄の操作 |
| 読みに戻す | Escape | 変換を取り消す |
| 削除 | Backspace | Mozcの未確定文字を編集 |
| カーソル・範囲選択 | 編集欄を操作 | 表示中の文字を確定し、新しい位置／選択範囲から入力 |
| 英語モード | 通常のキー入力 | 記号を含め半角の直接入力 |

Fn文節操作は変換中に限定しています。Fn/Sym経由でもAndroidから実際のASCII記号が届けばMozcへ入力します。修飾キー単独では未確定文字を確定しません。文字キーのまま届く未割り当てのFn/Sym入力は従来どおり通過し、その時点で未確定文字を確定します。独自のFn/Sym記号配置は追加していません。数字は従来どおり半角の直接入力です。

キー割り当ては `SegmentBindings` のデータとして処理から分離しています。将来の設定画面で置き換えられる構造ですが、設定画面自体は未実装です。パスワード・非テキスト欄は直接入力、学習は無効、ネットワーク権限はありません。

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
