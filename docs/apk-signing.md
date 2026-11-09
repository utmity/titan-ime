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

## 仕組み（このブランチ以降）

`app/build.gradle.kts` が以下の環境変数を読む:

| 環境変数 | 意味 |
|---|---|
| `TITAN_KEYSTORE_FILE` | キーストアファイルのパス（CI が一時ディレクトリに復元） |
| `TITAN_KEYSTORE_PASSWORD` | キーストアのパスワード |
| `TITAN_KEY_ALIAS` | 鍵のエイリアス |
| `TITAN_KEY_PASSWORD` | 鍵のパスワード |
| `TITAN_VERSION_CODE` | versionCode の上書き値 |

- 環境変数が揃っている場合、debug / release / preview の全ビルドがその鍵で
  署名され、versionCode は CI 実行ごとに増える値（`10000 + 実行番号`）になる
  → **CI APK 同士の上書き更新が成立する**。
- 揃っていない場合（ローカル開発や Secrets 未設定時）は従来通り、固定の
  versionCode 2 とマシンごとのデバッグ鍵になる。CI はその旨を警告として表示する。

ワークフロー側（`verify.yml` / `android-debug.yml`）は、Secrets
`TITAN_KEYSTORE_BASE64` が設定されていればキーストアをランナーの一時
ディレクトリに復元する。鍵のパスワード類は Gradle を実行するステップにだけ
渡し、ジョブ全体の環境変数には入れない。

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

4. 次の CI 実行から、成果物 APK は安定署名＋増加する versionCode になる。

## 既存インストールからの移行

- 現在インストール済みのビルド（旧 CI のデバッグ鍵で署名）は、**新しい安定署名の
  APK でも上書きできない**。安定署名へ移る際は一度だけ
  「アンインストール → 新しい APK をインストール」が必要で、その際そのパッケージの
  設定・辞書・学習データは失われる。再設定可能な項目は設定画面で戻せるが、
  Mozc の学習とユーザー辞書は戻せない。移行は任意のタイミングでよい
  （今のビルドを使い続けても壊れるわけではない）。
- PR の候補 UI などを既存インストールに触らず試したい場合は、Preview 成果物
  （パッケージ `dev.utmity.titanime.preview`）を使う。別パッケージとして
  並行インストールでき、設定・辞書も本体とは独立している。ただし Preview 同士でも
  署名が違う実行間では上書きできないため、別の実行の Preview を入れる場合は
  先に Preview をアンインストールする（Preview のデータは使い捨て前提）。

## 鍵の運用ルール

- キーストアファイルをリポジトリにコミットしない（`.gitignore` で
  `*.keystore` / `*.jks` / `*.p12` / `*.pfx` / `keystore.properties` を遮断済み）。
- 秘密は GitHub Secrets のみに置く。ログには出さない。
- 鍵・パスワードの変更は「上書き更新の断絶」を意味するので、やるなら
  再インストールを伴う移行として計画する。
