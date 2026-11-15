# 通常版・Preview共通の12キー入力

基準main: `e6a99893e2b1f190ef3b59de7b7a3b45e805409f`。
通常版 `dev.utmity.titanime` とPreview `dev.utmity.titanime.preview` は同じ入力機能を持ち、
別アプリ・別データ領域のままです。アプリID、署名設定、Mozc固定版、設定の保存先、
学習プロファイル、ユーザー辞書の形式・保存先は変更しません。

## 起動・切替

既存ユーザーに `input_mode` 設定がなければ **QWERTY**。日英設定 `japanese` は引き継ぎます。
初回起動で12キーへ移行する処理や、既存の設定・学習・辞書を消す処理はありません。
選択後は入力方式を `ime_settings/input_mode` に保存し、フィールド接続・IME再接続・
次回サービス起動時に復元します。不明な保存値はQWERTYとして扱い、他のデータを変更しません。
保存は方式変更時のみで、キー入力ごとのストレージ参照・保存は追加しません。

| 操作 | 動作 |
| --- | --- |
| モードボタンをタップ／方式切替ショートカット（標準Fn+Space） | QWERTY → かな → 英字 → 半角数字 → 全角数字・記号 → QWERTY |
| 12キー中のP | かな → 英字 → 半角数字 → 全角数字・記号 → かな |
| QWERTY中のP | 通常の文字入力 |
| QWERTYの日英ショートカット（標準Fn+Shift+Space）／QWERTYのモードボタン長押し | QWERTYを維持して日英切替 |
| Ctrl+Space／Shift+Ctrl+Space | AndroidのIME切替へ渡す（OS側設定に依存） |

方式切替・Pは未確定の読み／選択候補／数字スパンを先に確定し、候補ID・カーソル状態を
新セッションへ持ち越しません。Pのrepeatイベントは無視します。方式とQWERTYの日英は別に保存します。
従来の `shortcut_language`（または旧toggle設定）は方式切替として維持。
新しい `shortcut_qwerty_language` はQWERTY入力設定で変更可能です。
既存カスタム操作がFn+Shift+Spaceを使用している場合、新しい既定キーは割り当てず、
既存操作を優先します。その場合も設定の日英選択・モードボタン長押しで日英を変更できます。

## 配列と編集

全12キーモードで左右両方の文字キーを常時有効にします。
W/Y→1、E/U→2、R/I→3、S/H→4、D/J→5、F/K→6、Z/B→7、X/N→8、C/M→9。
G/T/V→0/*/#。かな・英字の循環はMozc標準表で、独自のかなテーブルはありません。
半角数字は毎回追加、全角数字・記号は既存の循環です（Gboard参考の記号近似は変更しません）。

カーソルは初期状態でQ＝左、A＝右、O＝上、L＝下。設定でQ/A＝上下・O/L＝左右へ入替可能。
読みの左右移動、同じ行の区切り、途中挿入、Backspace、選択置換、候補上下・文節左右は
既存Mozc/DPAD経路を利用します。確定済みテキストは通常のエディタ方向キーです。
QWERTYに12キー配列を適用せず、修飾キー・直接入力欄の迂回経路も維持します。

## 設定

両アプリでQWERTY入力／ガラケー入力（12キー）／共通設定／方式別操作ガイドを公開します。
共通設定から入力方式、QWERTY設定から日英状態と専用ショートカットを選べます。
実際の接続状態・入力方式と保存済みの日英設定は区別して表示します。
既存の設定キーを移動・消去せず、従来値を同じページのコントロールへ反映します。

## 検証と実機テスト

VerifyはJVMテスト、Debug/Preview/harness lint、Debug/Release/Previewビルド、
両アプリ用instrumentation APK、開発署名の検証を行います。
Signing smokeはmain基準版と候補版を同一の使い捨て鍵で署名し、
通常版IDの維持、20001→20002のversionCode、Debug/Release/Preview署名の一致、
Previewの別IDを検証します。本番鍵は使用せず、実機での上書き実行ではありません。

端末用テスト（デフォルトの方式／日英ショートカットを使用）：

```bash
# 通常版を選択して実行。Previewの場合はimePackageをdev.utmity.titanime.previewへ変更。
adb shell am instrument -w -e class dev.utmity.titanime.TwelveKeyInputTest -e imePackage dev.utmity.titanime dev.utmity.titanime.harness.test/androidx.test.runner.AndroidJUnitRunner
```

`-e otherIme <有効な別IMEのcomponent>` を追加するとIME往復テストも実行します。
`-e restartIme true` は対象IMEをforce-stopし、設定画面から再起動・再選択して方式復元を確認します。
これらは明示した場合だけ実行し、選択を対象Titan IMEへ戻します。
通常QWERTYの既存harnessスイートは、QWERTYを選んだうえで実行してください。
その日英切替キーをFn+Shift+Spaceへ追従させています。

T2Eで必要な確認：

- 既存の安定署名版へ上書き更新し、最初はQWERTY・以前の日英設定を維持すること。
- 設定・学習・ユーザー辞書・履歴の保持。両アプリの並行インストールとデータ分離。
- 方式変更→アプリ/フィールド変更→IME変更→サービス再起動で最後の方式を復元。
- 未確定読み・候補選択中・全角数字編集中の切替で重複・消失がないこと。
- 左右混在連打、Pの長押し、カーソル両配置、選択置換、区切り、候補／文節操作。
- QWERTYの日英切替、P通常入力、Alt・Fn編集、Ctrl制御キー。入力速度は実機比較が必要。
- Ctrl+SpaceのOSによるIME切替とキー解放。別IMEへの切替テストはCtrl+Space自体の検証の代用ではありません。

この環境にはAndroid端末/エミュレータがなく、instrumentationはビルドのみで未実行です。

## APKの入手と更新

- Draft PRのVerify成果物 `titan-ime-pr2-install-only` は通常版IDの**検証APK**。
  `titan-ime-preview-safe-install` は別IDのPreview検証APK。
  開発用versionCode 2・ランナー生成のデバッグ署名なので、安定署名の既存通常版への更新には使えません。
- 安定署名の更新用は、承認・main統合後の既存Distribution APKワークフローの
  `titan-ime-install-signed`。署名鍵とアプリIDを維持し、versionCodeは従来どおり
  `10000 + mainコミット数` で増加します。Preview安定署名版は`titan-ime-preview-signed`。
- このDraftではmainへマージせず、PRへ本番署名Secretsを渡す変更もしません。
  したがって今回のPRから既存端末へ上書きできる本番鍵署名APKを配布したとは主張しません。
- 既存APKの署名が別の旧デバッグ鍵の場合は、安定署名版でも上書きできません。
  **更新できない場合にデータ保持目的でアンインストールしないでください。**
  まずインストール済み版と更新APKの署名・versionCodeを照合してください。
  データ保持を伴う更新の実機確認は未実施です。署名運用は[apk-signing.md](apk-signing.md)参照。
