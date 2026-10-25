# Titan IME

Physical-keyboard-first Android IME for Unihertz Titan 2 Elite, with on-device Mozc kana-kanji conversion.

## Priorities and architecture

Mozc owns Japanese romaji, composition, conversion and candidates. Titan owns Android editor updates and physical-key routing. The custom Kotlin romaji converter has been removed; its regression cases now run against Mozc on the device.

Kana-kanji conversion, editor stability and usable physical-key settings are the current priorities. See [integration decisions, OSS licenses and build requirements](docs/mozc-integration.md) and [device validation](docs/device-validation.md).

## 操作一覧（実装済み）

| 機能 | 操作 | 動作・条件 |
| --- | --- | --- |
| 日英切り替え | **Fn+Space（標準）** | 設定でSym+Space／Alt+Spaceへ変更可。Ctrl+Shift+Jは廃止 |
| 日本語入力 | ローマ字 | Mozcでかな入力。独自変換器は使用しない |
| 記号 | `-` / `,` / `.` | 日本語モードで `ー` / `、` / `。`。未確定のままMozcへ入力 |
| 括弧 | `[` / `]` | 日本語モードで `「` / `」`。その他の引用符・記号もMozcへ渡す |
| 漢字変換・候補送り | Space | 入力中は変換、変換中は次候補 |
| 候補選択 | 候補をタップ | 選択後Enterで確定 |
| 前／次文節 | Fn+H / Fn+L | **変換中のみ**。選択文節を青背景で表示 |
| 文節を縮める／伸ばす | **Fn+U / Fn+O** | 変換中のみ。Shift不要 |
| 文節のタップ移動 | 前文節 / 次文節 | 候補欄のボタン |
| 外付け矢印キー | 左右 / Shift+左右 | 文節移動／文節長変更。T2Eには独立した矢印キーなし |
| 確定 | Enter | 未確定文字だけを確定。もう一度Enterで改行・編集欄の操作 |
| 読みに戻す | 候補欄の「読みに戻す」／Escape | かなに戻して途中編集を続ける |
| 削除 | Backspace | Mozcの未確定文字を編集 |
| かなの途中編集 | 未確定文字内をタップ | 未確定のままカーソル位置から挿入・Backspace・再変換 |
| 変換中のタップ | 変換文字列内をタップ | 文節を選択。文字を直す場合は「読みに戻す」 |
| 範囲外移動・範囲選択 | 編集欄を操作 | 表示中の文字を確定して移動／選択範囲の置換 |
| 英語モード | 通常のキー入力 | 記号を含め半角の直接入力 |

Fn文節操作は変換中に限定しています。文節長を変更すると候補が変わることがあるため、表記を確認し、必要ならSpaceで候補を選んでからEnterで確定してください。Alt/Fn/Sym経由でもAndroidから実際のASCII記号が届けばMozcへ入力します。T2EではAlt+I/N/Mがそれぞれ `- , .` で、日本語では `ー 、 。` になります。修飾キー単独では未確定文字を確定しません。文字キーのまま届く未割り当てのFn/Sym入力は従来どおり通過し、その時点で未確定文字を確定します。独自のFn/Sym記号配置は追加していません。数字は従来どおり半角の直接入力です。

キー割り当ては `SegmentBindings` のデータとして処理から分離しています。設定画面からH/L系とJ/K系を切り替えられます。パスワード・非テキスト欄は直接入力、変換学習は標準でオン、ネットワーク権限はありません。

未対応：未確定文字内の範囲選択を保ったままの編集、漢字変換状態のまま文字単位で挿入・削除する操作（「読みに戻す」を使用）、アプリが未確定範囲を報告しない編集欄。確認済みのタップ編集は専用テスト画面で、他アプリの互換性は未検証です。

## 設定画面

Androidの「設定 → システム → キーボード → 画面キーボード → Titan IME」から専用設定を開けます（端末により項目名が異なります）。AndroidへIMEのsettingsActivityとして登録しています。

- 日英切り替え：Fn+Space（標準）、Sym+Space、Alt+Space。1つを選ぶと即時保存・反映。
- 文節操作：Fn+H/L（標準）またはFn+J/K。前/次のキーを変更します。縮小/拡大は両方の設定でFn+U/Oです。Spaceと重複しません。
- 変換学習：オン（標準）／オフ。確定した変換をMozcが端末内で学習し、次回以降の候補へ反映。入力終了時に保存し、再起動後も保持します。保存量はMozc標準の管理に従います。オフにしても保存済み履歴は削除しません。
- 現在の入力モードと主要操作を表示。モードと割り当てはアプリ再起動後も保持。
- パスワード・非テキスト欄ではモードにかかわらず直接入力します。設定画面は既定IMEや有効IMEを変更しません。
- Ctrl+Spaceは引き続きAndroid側のIME切替用です。端末側の独自割り当てにより候補操作が届かない場合は他の候補を選んでください。

## Build and device tests (Windows)

Use JDK 17, Android SDK 35, and set ANDROID_HOME. The Gradle 8.9 wrapper builds the IME (`app`) and separate editor (`harness`).

First build the pinned engine on Linux/WSL2 with `bash scripts/build-mozc.sh`, or download the mozc-arm64 artifact from a successful trusted GitHub Actions run in this repository. Extract it and run `./scripts/import-mozc.ps1 -Source <extracted-directory>`. The importer checks revision and checksums. Engine and dictionary binaries are ignored by Git; Gradle fails clearly if they are missing. Initial device support is arm64-v8a only.

The full regression assumes Fn+Space and the H/L segment profile, normalizes English mode, disables learning for deterministic conversion checks, then enables learning to verify candidate adaptation. It finishes in English with learning on. To verify disk persistence, restart the IME process after the full run and separately run the LearningPersistenceTest instrumentation class.

Run `./scripts/test-device.ps1 -Serial <adb-serial> -InputSequence -SelectTitanIme` to build, install, test and restore the starting IME. Titan must already be enabled. Without -SelectTitanIme, select Titan manually. The runner never enables/disables IMEs or uninstalls packages. Use -SkipInstall to test installed APKs.

On this device, add `-FallbackIme <enabled-other-ime-id>` with -SelectTitanIme to switch away before updating Titan, avoiding the observed stale connection after installation. The original selection is restored in finally, even if instrumentation or log collection fails.

Results are saved under ignored test-results/. Inspect before sharing logs. Composer/conversion regressions run through the actual native engine in the separate harness instrumentation; there is no mock converter or old JVM composer test counted as Mozc validation.

Open the isolated editor with `adb -s <adb-serial> shell am start -n dev.utmity.titanime.harness/dev.utmity.titanime.InputTestActivity`. Its key probe records action, keyCode, scanCode, metaState and deviceId. A Debug-only IME notification lets tests wait for an actual input connection. Injected events test Android input routing, not physical hardware signals. Use `adb shell getevent -lt` while pressing known keys for physical capture, then stop capture.

## CI and review

GitHub Actions builds/caches the pinned Mozc engine and dictionary on Linux, packages Debug and unsigned Release APKs, runs Android Lint and the offline Windows cleanup regression. The latter uses mocked ADB to verify restoration after test/log failures. CI has no Titan device; use the device runner for native input tests.

Reports and Debug test APKs are retained for 7 days. CI Debug signing differs from local builds: use locally built APKs for install-r over an existing local installation. Release output is unsigned and is not distribution-ready.

Local checks: `./gradlew.bat :app:lintDebug :harness:lintDebug :app:assembleRelease` and `./scripts/test-runner-cleanup.ps1`.
