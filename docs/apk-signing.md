# APK 署名と CI 上書き更新の仕組み

## 何が起きていたか

Android は「同じパッケージ名でも、署名者が違えば上書きインストールを拒否する」
（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

このリポジトリには署名設定（`signingConfigs`）が無かったため、debug / preview
ビルドは各マシンの Android デバッグ鍵で署名されていた。GitHub Actions のランナーは
使い捨てで、実行のたびに新しいデバッグ鍵が生成される。つまり **CI の実行ごとに
署名者が変わり、どの CI APK も別の CI APK の上には上書きできなかった**。

実際に 2026-11-10、PR #3 の2回の CI 実行と PR #4 の CI 実行の成果物 APK を検証し、
パッケージ名（`dev.utmity.titanime`）と versionCode（2）が同一である一方、
署名証明書が3つとも異なることを確認した。versionCode が同じこと自体は
上書きを妨げない（同一署名なら同番号でも上書き可能）。妨げていたのは署名の不一致。

## 構成（責務の分離）

| ワークフロー | 役割 | 署名 Secrets | versionCode |
|---|---|---|---|
| `verify.yml` | 検証専用。全 PR と main への push で実行 | **一切渡さない**（PR でも push でも） | 固定 2（開発用） |
| `dist-apk.yml` | **配布用 APK の唯一の生成経路**。main への push と手動 dispatch のみ、かつジョブは main 以外では実行されない | 使う（未設定ならエラーで停止） | 下記の単一ルール |
| `signing-smoke.yml` | 署名経路の検証専用（手動 dispatch のみ） | 使わない（使い捨てテスト鍵をその場で生成） | テスト値 20001→20002 |

- 固定署名 APK は、レビューを経た main からのみ生成される。PR のビルド成果物は
  従来通りランナー生成のデバッグ鍵署名で、試作用（特に Preview の並行インストール用）。
- 旧 `android-debug.yml` は廃止した。Mozc 素材の取り込み手順が無く main への
  push のたびに失敗していた上、配布経路が複数あること自体が採番の衝突源だったため、
  `dist-apk.yml` に一元化した。

## versionCode の採番（一元化）

配布 APK の versionCode は **`10000 + main のコミット総数`** で単一に決まる。

- どのワークフロー・どの実行が作っても、同じコミットからは同じ番号になる。
- main が進む限り単調増加する。ワークフローの `run_number` はワークフローごとに
  独立したカウンタで、複数経路があると逆転が起きるため使わない。
- 注意: main の履歴を書き換える（force push）と単調性が崩れる。それをしない前提。

## 署名の仕組み

`app/build.gradle.kts` が以下の環境変数を読む:

| 環境変数 | 意味 |
|---|---|
| `TITAN_KEYSTORE_FILE` | キーストアファイルのパス（CI が一時ディレクトリに復元） |
| `TITAN_KEYSTORE_PASSWORD` | キーストアのパスワード |
| `TITAN_KEY_ALIAS` | 鍵のエイリアス |
| `TITAN_KEY_PASSWORD` | 鍵のパスワード |
| `TITAN_VERSION_CODE` | versionCode の上書き値 |

- 揃っている場合、debug / release / preview の全ビルドがその鍵で署名される
  （preview は debug を継承）。配布ワークフローは Secrets
  `TITAN_KEYSTORE_BASE64` からキーストアをランナーの一時ディレクトリに復元し、
  鍵パスワード類は Gradle を実行するステップにだけ渡す。**Secrets が無い配布
  ビルドは、使い捨て鍵で「配布用」を名乗る APK を作らないよう、エラーで停止する。**
- 揃っていない場合（検証ビルドとローカル開発）は従来通り、固定の versionCode 2 と
  マシンごとのデバッグ鍵になる。

## テスト鍵による検証（本番鍵なしで署名経路を確かめる）

Actions の「Signing smoke test」を手動実行する。ランナー内で使い捨てのテスト鍵を
生成し、versionCode 20001 と 20002 で2回ビルドして、**署名証明書が同一で
versionCode が増えていること**を自動で照合する（＝2つ目の APK が1つ目を上書き
できる条件そのもの）。本物の鍵や Secrets には一切触れない。鍵はランナーごと破棄される。

## 初期設定（リポジトリ所有者が一度だけ）

1. 署名鍵を生成する（有効期限は長く取る。Play 配布用ではない個人用 CI 鍵）:

   ```
   keytool -genkeypair -v -keystore titan-ime-ci.keystore -alias titan-ime-ci \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **キーストアファイルとパスワードを安全な場所にバックアップする**
   （パスワードマネージャ等）。**この鍵を失うと、以降の上書き更新は二度と
   できなくなる**（Android に復旧手段はない）。鍵を差し替える場合も同じで、
   その時点で一度アンインストール＋再インストールが必要になる。

3. Base64 化して GitHub Secrets に登録する:

   ```
   base64 -w0 titan-ime-ci.keystore   # 出力を TITAN_KEYSTORE_BASE64 に
   gh secret set TITAN_KEYSTORE_BASE64
   gh secret set TITAN_KEYSTORE_PASSWORD
   gh secret set TITAN_KEY_ALIAS
   gh secret set TITAN_KEY_PASSWORD
   ```

   （Settings → Secrets and variables → Actions からでも可。）

4. 次の main への push から、`dist-apk.yml` の成果物
   （`titan-ime-install-signed` / `titan-ime-preview-signed`）が安定署名＋
   一元化された versionCode になる。

## 既存インストールからの移行

### 通常の移行（データを残さない）

現在インストール済みのビルド（旧 CI のデバッグ鍵で署名）は、**新しい安定署名の
APK でも上書きできない**。アンインストール → 新しい APK のインストールが必要で、
そのパッケージの設定・辞書・学習データは失われる。

### run-as によるデータ移行（提案・実機未検証）

既存の PR #3 版は **debug ビルド**であり、成果物 APK のマニフェストで
`android:debuggable="true"` を実測確認した。debuggable なアプリには
`adb shell run-as <パッケージ>` でアプリ権限のシェルが使え、プライベート
データ領域を読み書きできる。`allowBackup="false"` のため `adb backup` 経路は
使えないが、run-as 経路は残っている。配布 APK も debug ビルドタイプなので、
移行先でも run-as が使える。

移行対象のデータ（コードから確認した保存先）:

| データ | パス（アプリのデータ領域内） |
|---|---|
| IME 設定 | `shared_prefs/ime_settings.xml` |
| クリップボード履歴 | `shared_prefs/clipboard_history.xml` |
| ユーザー辞書 | `no_backup/mozc/user_dictionary.db`（と `.bak`） |
| Mozc プロファイル（学習データ） | `no_backup/mozc/` 一式 |
| （不要）Mozc 辞書データ | `files/mozc-c4b2aef.data` はアセットから再生成される |

手順の骨子（PC に adb がある前提）:

1. Titan IME から別の IME に切り替え、`adb shell am force-stop dev.utmity.titanime`
   でプロセスを止める（書き込み競合を避ける）。
2. バックアップ:
   `adb exec-out run-as dev.utmity.titanime tar -cf - shared_prefs no_backup files > titan-ime-backup.tar`
   （`cache` は含めない。個別ファイルだけでもよい。）
3. 旧アプリをアンインストールし、配布ワークフローの署名済み APK をインストールする。
   **まだ Titan IME を有効化・起動しない。**
4. 復元: `adb shell run-as dev.utmity.titanime tar -xf - < titan-ime-backup.tar`
   （adb のバージョンにより標準入力のパイプ挙動が違う場合は、上記の対象
   ファイルを1つずつ `run-as ... sh -c 'cat > <パス>'` で書き戻す。）
5. Titan IME を有効化し、設定・ユーザー辞書・変換学習が引き継がれているか確認する。

見込みと注意:

- PR #3 / #4 は保存形式を変更していない（設定キー、ユーザー辞書の protobuf
  フォーマット、Mozc のバージョンは不変）ため、ファイルコピーで移行できる
  見込みは高い。run-as のシェルはアプリ自身の UID で動くので、復元した
  ファイルの所有者も正しくなる。
- ただし**実機では未検証の提案**である。特に Mozc ネイティブプロファイルの
  移植性は上流の保証対象外で、学習データだけは引き継げない可能性が残る。
- 復元は新規インストール直後、アプリが自分の辞書やセッションを書き始める前に行うこと。

### Preview で試す場合

PR の候補 UI などを既存インストールに触らず試す場合は、Preview 成果物
（パッケージ `dev.utmity.titanime.preview`）を使う。別パッケージとして
並行インストールでき、設定・辞書も本体とは独立している。署名が違う実行間では
Preview 同士でも上書きできないため、別の実行の Preview を入れる場合は
先に Preview をアンインストールする（Preview のデータは使い捨て前提）。
配布ワークフローの Preview（`titan-ime-preview-signed`）は安定署名なので、
そちら同士は上書き更新できる。

## 鍵の運用ルール

- キーストアファイルをリポジトリにコミットしない（`.gitignore` で
  `*.keystore` / `*.jks` / `*.p12` / `*.pfx` / `keystore.properties` を遮断済み）。
- 秘密は GitHub Secrets のみに置き、渡すのは配布ワークフローの Gradle
  ステップだけにする。検証ワークフローには渡さない。ログには出さない。
- 鍵・パスワードの変更は「上書き更新の断絶」を意味するので、やるなら
  再インストールを伴う移行として計画する。
