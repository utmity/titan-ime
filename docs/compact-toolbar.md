# コンパクトな入力ツールバー（2026-10-30）

前段のUI整理は32b98d8。その後の依頼で、常設マイク・モード表示・候補一覧と
履歴検索削除まで実装範囲が拡張された。予測候補の幅・省略・順位は変更しない。

## 操作

- 左の「あ / A」：現在モード。48dpのタップ領域で切り替え。Fn+Spaceも従来どおり。
  切り替えを実行した場合は従来と同じ未確定確定処理を行う。
- マイク：通常バーが見えている間だけ日英両モードで表示。1タップ開始、認識中は
  停止表示。停止タップ/キャンセル/物理キーで中止できる。パスワード・直接入力欄は非表示。
  「…」の重複した音声入力項目は削除済み。初回権限許可後のみ再度開始が必要。未確定がある場合は勝手に確定せず案内する。
- 候補一覧：複数候補がある場合の展開ボタン。最大3件のページで表示し、各候補は
  横スクロール可能で、省略・幅上限を加えない。Mozcの全候補が提供されていれば
  そのIDと順序を使い、ない場合は従来の候補ウィンドウを使う。
- 一覧の選択：標準はFn+K/Jで前/次。変換中文節設定がFn+J/Kの場合だけ、一覧選択は
  Fn+H/Lに入れ替える。元の文節操作とFn+U/Oを奪わない。Enterまたは候補タップで確定。
  Spaceは従来のMozc候補切り替え。Android Back/収納ボタンで閉じるだけなら確定しない。
- 履歴：Fn+V（設定でFn+B/無効）→Fn+K/J→Enter。検索は削除。通常文字やBackspaceは
  履歴を閉じて通常入力へ渡す。ピン/個別削除/確認付き全削除/件数設定は維持。
- メニュー：Undo/Redoは必ず同じ行に隣接。入力先の標準操作を1回要求する従来方式。
- 設定：前段で導入した5分類を維持。保存キーやデータ形式の移行は不要。

メニュー・履歴・文字変換・一覧は入力接続へsetSelectionを送って開閉しない。
古い選択範囲を保存・復元する処理も加えていない。貼り付け・文字変換・候補確定など
明示操作だけが編集を実行する。音声は従来の入力世代/接続・表示状態の検証を維持。

## Android下部バー

実機の読み取り結果：Android 16/API 36、navigation_mode=2（ジェスチャー）、
show_ime_with_hard_keyboard=0。「物理キーボード」標準設定には画面キーボード使用、
キーマップ、修飾キー、リピート等があり、下部の閉じる/切り替えボタンだけを隠す項目は
確認できなかった。全OEM設定に項目がないと断定するものではない。

標準ジェスチャー設定Intentはこの端末で解決されなかった。設定値は変更していない。
「画面キーボードを使用」オフと、IMEが表示中のシステム操作バーの非表示は別である。

AndroidのIMEナビゲーション領域はフレームワークが管理する。一般Activity向けの
immersive hideだけでIMEの閉じる/切替ボタンを安全に除去できると仮定しない。
requestHideSelfは候補バーも閉じるため代替にはしない。非公開API、root、ADB設定変更、
システムViewの強制削除は実装していない。

### captionBar再調査後の実装

Android 16以降の通常入力欄では、公開APIの
`WindowInsetsController.hide(WindowInsets.Type.captionBar())` をIMEウィンドウに要求する。
AOSP Android 16のNavigationBarControllerはIME専用captionBarの可視性から
システムの下矢印/切り替え領域をGONEにする。ジェスチャー用IMEナビゲーションを
描く設定が前提で、OEMやナビゲーション方式による差がある。

T2EでAPI単独を試すとボタンは消えたが、Titanの固定32dp余白が残った。
そこで、captionBarの非表示と非ゼロの本来のInsetsを公開APIで確認できた場合だけ
固定余白を外す。navigationBarsの実Insetsは維持する。未対応・旧OSでは従来の余白を維持。
パスワード/直接入力欄ではcaptionBarをshowして、既存のシステム操作へ戻す。

Android 16の公開コールバック`onCustomImeSwitcherButtonRequestedVisible`に応じて
候補バーに「⌨」を表示し、標準IMEピッカーを開く。「…」と設定画面の切り替えも維持。
このコールバックを正規にoverrideするためcompileSdkのみ36へ更新（targetSdkは35のまま）。
音声開始は通常バーのマイクに一本化し、メニューの重複項目だけを削除した。

実測（同じ専用入力欄、横向き1200×1076px、48dpバー=90px）：

| 状態 | バー上端 | バー下端 | 画面下端までの余白 |
| --- | ---: | ---: | ---: |
| 従来 | 926px | 1016px | 60px |
| hideのみ | 926px | 1016px | 60px |
| hide＋Insets連動余白 | 986px | 1076px | 0px |

表示色の変更とは別に、入力UIの占有高さを150pxから90pxへ60px（32dp）削減した。
システムナビゲーション方式・画面設定は変更していない。全端末/回転状態で0pxになる
保証ではなく、残る実Insetsと安全なフォールバックを優先する。

## データとプライバシー

ClipboardHistoryの保存実装・SharedPreferencesキー・ピン情報は変更しない。
検索は一時UI状態だったため削除時のデータ移行や履歴削除は不要。
マイクはユーザー操作でのみ開始。音声の外部送信可能性、取消・重複結果抑止、
自動言語判別のサービス依存、パスワード欄の制限は従来どおり。

## 検証の区別

実機注入テストで一覧開閉、選択保持、確定後の追加入力、モード/マイク表示、
Undo/Redo隣接、検索なし通常入力を確認する。マイクの自動テストは未確定ガードを使い、
録音を開始しない。実際のマイクからの発話・復帰は人間の別確認として記録する。
履歴テストは一時項目だけ追加/削除し、既存項目とピンが変わらないことを比較する。

## 公開資料

- [WindowInsets.Type](https://developer.android.com/reference/android/view/WindowInsets.Type)
- [System bar visibility](https://developer.android.com/develop/ui/views/layout/immersive)
- [InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [AOSP IME NavigationBarController](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/inputmethodservice/NavigationBarController.java)
