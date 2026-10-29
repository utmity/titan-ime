# Titan IME

## 編集・入力支援

| 操作 | 初期割り当て・利用方法 |
| --- | --- |
| 通常のカーソル移動 | Fn+H/L/K/J：左/右/上/下（設定でWASD配置または無効化） |
| 範囲選択 | Shift+Fn+方向相当。左右は文字境界と選択アンカーを保持 |
| 単語移動 | Ctrl+Fn+H/L（入力先の対応に依存） |
| 単語削除 | 設定でFn+Backspaceを有効化（標準オフ） |
| 変換中の文節操作 | 既存のFn+H/L、Fn+U/Oを優先 |
| 履歴パネル | Fn+V（設定でFn+B/無効化）、または候補バーの「…」 |
| 履歴の検索・貼り付け | 文字入力で検索、Fn+J/Kで選択、Enterまたはタップで貼り付け、Escで閉じる |
| 履歴保存 | 設定から有効化。標準オフ、10/20/50件、ピン留め・個別削除・全削除 |
| Undo / Redo | 「…」から入力先へ標準操作を要求。Ctrl+Z等は従来どおりアプリへ転送 |
| 自動入力 | 「…」から入力先へ要求、設定からAndroidのサービス選択画面へ移動 |
| 別IMEへの切り替え | 「…」または設定の「入力方法を切り替える」 |

未確定中の左右移動は読みを編集します。上下・単語移動と履歴の貼り付けは
未確定文字列を確定してから実行します。メニューを開閉するだけでは確定しません。
パスワード・直接入力欄のバイパスは維持します。インライン自動入力は通常欄向けの
API連携を実装していますが、Google/Bitwarden実機連携とパスワード欄の表示は未対応・未検証です。
操作の制約・履歴の保存対象は[編集支援の仕様](docs/editing-support.md)を参照してください。
ChatGPTの細切れ確定とSpace連続変換は[実機の状態遷移と修正](docs/batch-editor-synchronization.md)に記録しています。

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
| 予測候補 | Spaceを押す前に候補をタップ | 入力に応じて更新。タップで確定。「そのまま」で読みを確定 |
| 通常変換の候補選択 | Space後に候補をタップ | 選択後Enterで確定 |
| 訂正候補 | 「（訂正）」付き候補をタップ | Mozc標準の読み訂正。入力文字の自動置換はしない |
| 前／次文節 | Fn+H / Fn+L | **変換中のみ**。選択文節を太字・下線で表示（入力欄の色は変更しない） |
| 文節を縮める／伸ばす | **Fn+U / Fn+O** | 変換中のみ。Shift不要 |
| 文節のタップ移動 | 前文節 / 次文節 | 候補欄のボタン |
| 外付け矢印キー | 左右 / Shift+左右 | 文節移動／文節長変更。T2Eには独立した矢印キーなし |
| 確定 | Enter | 未確定文字だけを確定。もう一度Enterで改行・編集欄の操作 |
| 読みに戻す | 候補欄の「読みに戻す」／Escape | かなに戻して途中編集を続ける |
| 削除 | Backspace | Mozcの未確定文字を編集 |
| かなの途中編集 | 未確定文字内をタップ | 未確定のままカーソル位置から挿入・Backspace・再変換 |
| 変換中のタップ | 変換文字列内をタップ | 文節を選択。文字を直す場合は「読みに戻す」 |
| かなの範囲選択 | 未確定文字内を長押しして選択 | ローマ字・記号で置換、Backspace/Deleteで選択範囲を削除。未確定を維持 |
| 範囲外移動・変換中の範囲選択 | 編集欄を操作 | 表示中の文字を確定して移動／選択範囲の置換 |
| 英語モード | 通常のキー入力 | 記号を含め半角の直接入力 |

Fn文節操作は変換中に限定しています。文節長を変更すると候補が変わることがあるため、表記を確認し、必要ならSpaceで候補を選んでからEnterで確定してください。Alt/Fn/Sym経由でもAndroidから実際のASCII記号が届けばMozcへ入力します。T2EではAlt+I/N/Mがそれぞれ `- , .` で、日本語では `ー 、 。` になります。修飾キー単独では未確定文字を確定しません。文字キーのまま届く未割り当てのFn/Sym入力は従来どおり通過し、その時点で未確定文字を確定します。独自のFn/Sym記号配置は追加していません。数字は従来どおり半角の直接入力です。

キー割り当ては `SegmentBindings` のデータとして処理から分離しています。設定画面からH/L系とJ/K系を切り替えられます。パスワード・非テキスト欄は直接入力、変換学習は標準でオン、ネットワーク権限はありません。

未対応：漢字変換状態のまま文字単位で挿入・削除する操作（「読みに戻す」を使用）、アプリが未確定範囲を報告しない編集欄。専用テスト画面ではタップ・長押し編集と、未確定範囲のCtrl+C/X/V・全選択・貼り付け後の再入力を確認済みです。クリップボード操作はキー注入による確認で、ChatGPTなど他アプリの互換性は未検証です。

## 設定画面

Androidの「設定 → システム → キーボード → 画面キーボード → Titan IME」から専用設定を開けます（端末により項目名が異なります）。AndroidへIMEのsettingsActivityとして登録しています。

- **入力方法を切り替える**：Android標準のIME選択画面を開きます。候補バー・Mozc・Titanの選択状態に依存せず、有効なIMEをユーザーが選択します。他IMEの有効化や自動選択はしません。
- 日英切り替え：Fn+Space（標準）、Sym+Space、Alt+Space。1つを選ぶと即時保存・反映。
- T2E内蔵キーボードのAlt：「Altで数字・記号（標準）」では実機キーマップの文字を入力します（Alt+R＝3、Alt+I/N/M＝-/,/.）。日本語テキスト欄では記号をMozcへ渡します。「Altをアプリへ渡す（Meta）」では元のAltイベントをアプリへ渡します。Ctrl+Altは常にアプリ側へ渡します。外付けキーボードにはこの文字入力処理を適用しません。
- 文節操作：Fn+H/L（標準）またはFn+J/K。前/次のキーを変更します。縮小/拡大は両方の設定でFn+U/Oです。Spaceと重複しません。
- 変換学習：オン（標準）／オフ。確定した変換をMozcが端末内で学習し、次回以降の候補へ反映。入力終了時に保存し、再起動後も保持します。保存量はMozc標準の管理に従います。オフにしても保存済み履歴は削除しません。
- 予測変換：オン（標準）／オフ。入力中の候補を最大8件要求。オフでもSpace変換は使えます。
- 誤入力の訂正候補：オン（標準）／オフ。Mozc標準の読み訂正を使用。予測・訂正設定は保存され、次に編集欄へ戻ったときに反映します。
- ユーザー辞書：登録・検索・編集・削除、UTF-8 TSVの入出力。学習オフでも登録語は使えます。
- 現在の入力モードと主要操作を表示。モードと割り当てはアプリ再起動後も保持。
- パスワード・非テキスト欄ではモードにかかわらず直接入力します。設定画面は既定IMEや有効IMEを変更しません。
- Ctrl+Spaceは引き続きAndroid側のIME切替用です。端末側の独自割り当てにより候補操作が届かない場合は他の候補を選んでください。
- アプリ向けのCtrl/Meta操作は元のキーイベントを渡します。長押し中に修飾キーが変わった場合も、一度Downを渡したキーの最後のUpを保持します。

変換候補は最初のSpaceから表示します。Mozcがデスクトップ用候補ウィンドウをまだ開いていない場合も、変換中の全候補から最大8件を表示します。入力先が未確定文字を書き換えた場合は、その文字を保持して古い変換状態を破棄します。Androidが入力ビューを隠す際に確定した文字は、自動で未確定に戻しません。詳しい検証範囲・制約は[編集欄の互換性](docs/editor-compatibility.md)を参照してください。

Discordの実機で確認したチャンネル名欄は非テキスト・可視パスワード相当の入力種別を通知しており、Titanでは直接入力になります。この欄での日本語変換は未解決です。安全な入力種別の扱いを一律に解除する例外は追加していません。

## ユーザー辞書の使い方

1. Titan IME設定 → **ユーザー辞書** → **単語を登録**を開きます。
2. 単語・読み・品詞を入力して保存します。カタカナ（半角を含む）の読みはひらがなへ正規化します。
3. 日本語モードで読みを入力し、予測候補またはSpace変換から登録語を選びます。読みが複数文節に分かれた場合はFn+Oで登録語の長さまで伸ばしてください。
4. 辞書一覧の単語をタップして編集・削除できます。検索欄は単語と読みを検索します。

品詞は名詞・固有名詞・人名・組織・地名・記号・顔文字・副詞に対応。読みはひらがなと長音符で100文字以内、単語は100文字かつUTF-8で300バイト以内です。空欄・制御文字・不正な読みは拒否します。同じ読みで複数単語を登録できます。同じ読み・単語・品詞の重複登録は拒否します。最大10000件、一覧は検索結果の先頭100件を表示します。

保存先は端末内のMozcユーザー辞書です。保存後にJNI経由で再読み込みし、次の入力から反映します。IMEの再起動は不要で、端末・アプリ再起動後も保持します。削除はユーザー辞書だけが対象です。同じ単語がシステム辞書や過去の学習にあれば、その候補までは消えません。

**インポート／エクスポート**はAndroid標準ファイル選択画面を使います。広範なストレージ権限は不要です。形式はUTF-8（読込時はBOM可）、1行1単語の `読み<TAB>単語<TAB>品詞<TAB>コメント`。コメントは省略可で、画面では編集しませんが保存・書き出し時に保持します。MozcのTSV形式のうち上記8品詞に対応し、未知の品詞や不正行があれば**全件取り込みを中止**します。完全な重複はスキップし、既存単語を上書きしません。入力ファイルは2MBまで、空行と`#`で始まる行は無視します。書き出したファイルには登録語が含まれるため、保存先は自分で選択してください。

## 予測・訂正の対応範囲

Mozc標準の辞書・履歴候補を使用し、独自の全キー組合せ探索や自動置換は行いません。日本語モードの通常テキスト欄だけに表示します。入力中の読みとカーソル位置は保持し、候補のタップで確定します。Space後の文節変換は従来どおりです。

- 実機エンジンで確認：`fuinki` → 未確定「ふいんき」のまま「雰囲気（訂正）」を提示。`shi` / `si` →「し」。`nihonngo` →「にほんご」→「日本語」。最後の例はローマ字の`nn`処理であり、一般的な余分キー除去ではありません。
- 訂正オンではMozcの濁点・半濁点・小書きかなを区別しない候補検索も有効にします。`katukou`（かつこう）→「学校」、`hiyouin`→「病院」を予測し、`han`→「パン」はSpace変換で提示します。これらは公開の訂正属性を持たないため「（訂正）」表示は付きません。元の読みは自動置換せず、「そのまま」で確定できます。`gakukou`→「学校」のような一般的な脱字補正は対象外です。
- 未対応の実測例：`nihngo`（脱字）、`nihonfo`（g/f誤打鍵）から「日本語」、`sunkai`から「深海」は候補に出ませんでした。任意の隣接キー誤打鍵・脱字・余分な文字の訂正は未対応です。
- 固定版OSS Mozcの追加タイプミスモデルはstubで、設定フラグだけでは一般的な打鍵補正になりません。T2E独自の補正生成層は今回追加していません。固有名詞や英数字を推測して勝手に書き換えません。

詳しいAPI調査とテストの区別は[予測・辞書統合](docs/prediction-dictionary.md)を参照してください。

## Build and device tests (Windows)

TermuxでのAlt文字入力とMeta設定、Ctrl制御入力の違い、検証範囲は[修飾キー互換性](docs/modifier-compatibility.md)を参照してください。TermuxのTYPE_NULL欄では日本語の未確定入力は未対応です。

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
