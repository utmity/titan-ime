package dev.utmity.titanime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*

/** Separate activity pages; preferences retain their original keys and stores. */
class SettingsActivity : Activity() {
    private lateinit var preferences: ImePreferences
    private lateinit var mode: TextView
    private val preview get() = packageName == "dev.utmity.titanime.preview"
    private var page = SettingsPage.HOME
    private val modeChanged: () -> Unit = { updateMode() }

    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        preferences = ImePreferences(this)
        page = SettingsPage.resolve(state?.getString("page") ?: intent.getStringExtra("page"))
        title = page.title
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
                }
                insets
            }
        }
        fun text(value: String) = TextView(this).apply {
            text = value; textSize = 18f; setTextColor(0xff202124.toInt()); content.addView(this)
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; setOnClickListener { action() }; content.addView(this)
        }
        fun navigate(destination: SettingsPage) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", destination.name))
        }
        if (page != SettingsPage.HOME) button("戻る") { finish() }
        text(if (page == SettingsPage.HOME) {
            if (preview) "Titan IME Preview 設定" else "Titan IME 設定"
        } else page.title)
        mode = text("").apply { tag = "mode-status" }
        fun choices(key: String, title: String, values: List<Pair<String, String>>, current: String, save: (String) -> Unit) {
            text(title)
            val group = RadioGroup(this).apply { id = stableId(key); tag = key }
            val ids = mutableMapOf<Int, String>()
            values.forEach { (value, label) ->
                val choice = RadioButton(this).apply {
                    id = stableId("$key:$value"); tag = "$key:$value"; text = label
                }
                ids[choice.id] = value
                group.addView(choice)
                if (value == current) group.check(choice.id)
            }
            group.setOnCheckedChangeListener { _, id -> ids[id]?.let(save) }
            content.addView(group)
        }
        fun shortcutButton(action: String, title: String) {
            val button = Button(this)
            fun description() = "$title：${preferences.shortcut(action)?.label() ?: "割り当てなし"}"
            button.text = description()
            button.setOnClickListener {
                var titanFnHeld = false
                val hint = TextView(this@SettingsActivity).apply {
                    text = "割り当てるキーの組み合わせを押してください。通常の文字入力を妨げないよう修飾キーが必要です。"
                    setPadding(24, 18, 24, 18)
                    setTextColor(0xff202124.toInt())
                }
                val dialog = android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(title)
                    .setView(hint)
                    .setNegativeButton("キャンセル", null)
                    .apply {
                        if (action != "language") setNeutralButton("割り当てなし") { _, _ ->
                            preferences.setShortcut(action, null)
                            button.text = description()
                        }
                    }.create()
                dialog.setOnKeyListener { _, code, event ->
                    if (code == 403 && event.scanCode == 251) {
                        titanFnHeld = event.action == android.view.KeyEvent.ACTION_DOWN
                        true
                    } else if (code == android.view.KeyEvent.KEYCODE_BACK) {
                        false
                    } else if (event.action != android.view.KeyEvent.ACTION_DOWN || event.repeatCount != 0) {
                        true
                    } else {
                        val binding = ShortcutBinding.capture(event, titanFnHeld || event.isFunctionPressed)
                        val reason = when {
                            binding == null -> "Ctrl・Alt・Fn・Sym・Metaなどと組み合わせてください。"
                            binding.reserved() || SystemImeShortcut.reserved(binding) -> "この組み合わせはAndroidのIME切替・文節操作・カーソル編集用に予約されています。"
                            else -> preferences.shortcuts().ownerOf(binding, action)?.let {
                                "「${when (it) { "language" -> "入力方式切替"; "qwerty_language" -> "QWERTY日英切替"; "history" -> "履歴"; else -> "文字変換" }}」で使用中です。"
                            }
                        }
                        if (reason != null) hint.text = reason
                        else {
                            preferences.setShortcut(action, binding)
                            button.text = description()
                            dialog.dismiss()
                        }
                        true
                    }
                }
                dialog.show()
            }
            content.addView(button)
        }

        when (page) {
            SettingsPage.HOME -> {
                text("Version 0.1.1-pr2 ・ build 2")
                button("入力方法を切り替える") {
                    getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker()
                }
                for (destination in SettingsPage.homePages()) button(destination.title) { navigate(destination) }
            }
            SettingsPage.QWERTY -> {
                choices("japanese", "QWERTYの日本語・英語", listOf(
                    "ja" to "日本語（ローマ字かな入力）", "en" to "英語（直接入力）"
                ), if (preferences.japanese) "ja" else "en") { preferences.japanese = it == "ja" }
                shortcutButton("qwerty_language", "QWERTYの日本語・英語切替")
                text("QWERTYの日英切替は上のショートカット（標準Fn+Shift+Space）、または画面モードボタンの長押し。共通設定の入力方式切替（標準Fn+Space）と画面ボタンのタップはQWERTYを含む方式切替です。Pは通常の文字です。")
                choices("alt_text", "T2E内蔵キーボードのAlt", listOf("text" to "Altで数字・記号（標準）", "meta" to "Altをアプリへ渡す（Meta）"), if (preferences.altText) "text" else "meta") { preferences.altText = it == "text" }
                choices("editing", "通常編集のカーソル操作", listOf("on" to "有効：Fnと方向キー配置を併用", "off" to "無効（アプリへ渡す）"), if (preferences.editing) "on" else "off") { preferences.editing = it == "on" }
                choices("editing_layout", "通常編集の方向キー配置", listOf("hjkl" to "H/L/K/J：左/右/上/下", "wasd" to "A/D/W/S：左/右/上/下"), preferences.editingLayout) { preferences.editingLayout = it }
                text("Shift併用：範囲選択、Ctrl併用：左右の単語移動。変換中は文節操作を優先。上下・単語移動時は未確定を確定します。直接入力欄は対象外です。")
                choices("word_delete", "単語削除", listOf("off" to "無効（標準）", "on" to "Fn+BackspaceをCtrl+Backspaceとして送る"), if (preferences.wordDelete) "on" else "off") { preferences.wordDelete = it == "on" }
            }
            SettingsPage.TWELVE_KEY -> {
                text(twelveKeyGuide())
                choices("twelve_key_swap_cursor_axes", "12キーのカーソル配置（QWERTYには適用しません）", listOf(
                    "default" to "Q＝左・A＝右 ／ O＝上・L＝下",
                    "swapped" to "Q＝上・A＝下 ／ O＝左・L＝右"
                ), if (preferences.twelveKeySwapCursorAxes) "swapped" else "default") { preferences.twelveKeySwapCursorAxes = it == "swapped" }
            }
            SettingsPage.COMMON -> {
                choices("input_mode", "入力方式（次回起動時にも復元）", InputMode.entries.map { it.name to it.description }, preferences.inputMode.name) {
                    preferences.inputMode = InputMode.restore(it)
                }
                shortcutButton("language", "入力方式の切替（QWERTYを含む）")
                choices("toolbar_mode", "候補バーの表示", listOf("always" to "通常時も表示", "compose" to "入力・変換中のみ表示", "conversion" to "変換中のみ表示"), preferences.toolbarMode) { preferences.toolbarMode = it }
                text("バーを隠している間も物理キーで入力できます。変換中やメニュー表示中は必要に応じてバーが現れます。")
                choices("prediction", "入力中の予測候補", listOf("on" to "予測変換オン", "off" to "予測変換オフ"), if (preferences.prediction) "on" else "off") { preferences.prediction = it == "on" }
                choices("correction", "Mozc標準の訂正候補（自動置換なし）", listOf("on" to "訂正候補オン", "off" to "訂正候補オフ"), if (preferences.correction) "on" else "off") { preferences.correction = it == "on" }
                text("訂正オンでは濁点・半濁点・小書きかなの違いも候補検索に含めます。元の読みは「そのまま」で確定できます。一般的な脱字や隣接キーの打ち間違いには未対応です。")
                choices("learning", "端末内の変換学習", listOf("on" to "変換学習オン", "off" to "変換学習オフ"), if (preferences.learning) "on" else "off") { preferences.learning = it == "on" }
                button("ユーザー辞書") { startActivity(Intent(this, UserDictionaryActivity::class.java)) }
                shortcutButton("history", "クリップボード履歴")
                shortcutButton("transform", "選択文字の変換")
                choices("segments", "文節操作（変換中のみ）", listOf("hl" to "Fn+H/L：前/次・Fn+U/O：縮小/拡大", "jk" to "Fn+J/K：前/次・Fn+U/O：縮小/拡大"), preferences.segments) { preferences.segments = it }
                val history = ClipboardHistory(this)
                choices("history_enabled", "クリップボード履歴（端末内保存）", listOf("off" to "保存しない（標準）", "on" to "保存する"), if (history.enabled) "on" else "off") { history.enabled = it == "on" }
                choices("history_limit", "履歴件数（ピン留めを含む）", listOf("10" to "10件", "20" to "20件", "50" to "50件"), history.limit.toString()) { history.limit = it.toInt() }
                text("通常入力欄がある間の新しいコピーのみ保存。機密フラグ付き・パスワード欄・URI・1万文字超は保存しません。機密フラグのない秘密情報は識別できないため、必要に応じて保存を無効にしてください。オフにしても保存済み履歴は残ります。")
                button("全履歴を削除…") {
                    android.app.AlertDialog.Builder(this).setMessage("ピン留めを含む保存済み履歴を削除しますか？")
                        .setNegativeButton("キャンセル", null).setPositiveButton("削除") { _, _ -> history.clear() }.show()
                }
                choices("voice_language", "音声認識の優先言語", listOf("auto" to "自動（端末の言語を基準）", "ja" to "日本語優先", "en" to "英語優先"), preferences.voiceLanguage) { preferences.voiceLanguage = it }
                text("対応サービスでは日英を自動切替。音声が端末の認識サービスへ送信される場合があります。")
                button("Androidの自動入力設定") {
                    val request = Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                        .setData(android.net.Uri.parse("package:$packageName"))
                    if (request.resolveActivity(packageManager) != null) startActivity(request)
                }
            }
            SettingsPage.GUIDE -> {
                button(SettingsPage.QWERTY_GUIDE.title) { navigate(SettingsPage.QWERTY_GUIDE) }
                button(SettingsPage.TWELVE_KEY_GUIDE.title) { navigate(SettingsPage.TWELVE_KEY_GUIDE) }
            }
            SettingsPage.QWERTY_GUIDE -> text(qwertyGuide())
            SettingsPage.TWELVE_KEY_GUIDE -> text(twelveKeyGuide())
        }
        fun style(view: View) {
            if (view is Button && view !is CompoundButton) {
                view.setTextColor(0xff202124.toInt())
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(0xfff1f3f4.toInt())
                view.elevation = 0f; view.stateListAnimator = null
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
        }
        style(content)
        setContentView(ScrollView(this).apply {
            id = stableId("scroll:${page.name}")
            setBackgroundColor(android.graphics.Color.WHITE); addView(content)
        })
    }

    override fun onStart() {
        super.onStart()
        InputModeStatus.observe(modeChanged)
    }

    override fun onStop() {
        InputModeStatus.removeObserver(modeChanged)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateMode()
    }

    private fun updateMode() {
        mode.text = InputModeStatus.active?.let { "現在の入力モード：$it" }
            ?: "入力欄未接続（QWERTYの日英設定：${if (preferences.japanese) "日本語" else "英語"}）"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page.name)
        super.onSaveInstanceState(outState)
    }

    private fun stableId(key: String) = (key.hashCode() and 0x00ffffff) + 1

    private fun qwertyGuide() = """
        【QWERTY入力】
        ${preferences.shortcut("language")?.label() ?: "未設定"}または画面ボタンのタップ：QWERTYを含む入力方式の切替。
        ${preferences.shortcut("qwerty_language")?.label() ?: "未設定"}またはQWERTY画面ボタンの長押し：日本語・英語切替。
        現在の入力方式とQWERTYの日英設定を保存します。共通設定・QWERTY入力設定からも変更できます。
        日本語入力中はSpaceで変換・候補送り、Enterで確定。Backspaceで削除。
        - , .：ー 、 。を入力。元のローマ字候補をタップすると英字で確定します。
        文節移動：${if (preferences.segments == "jk") "Fn+J/K" else "Fn+H/L"}。文節長変更：Fn+U/O。
        通常編集はQWERTY入力設定のFn＋方向キー配置。Shiftで選択、Ctrlで単語移動。
        Pは通常の文字入力です。

        【共通ツール】
        ${preferences.shortcut("history")?.label() ?: "未設定"}：クリップボード履歴。
        ${preferences.shortcut("transform")?.label() ?: "未設定"}：選択文字の変換。
        候補バー、辞書、学習、履歴、音声、自動入力は共通設定で変更します。
        バーのマイクは音声入力、…は履歴・文字変換・Undo/Redo・自動入力・IME切替です。
        Androidや入力先アプリに先取りされるキーは利用できない場合があります。
        パスワード・数字欄などは直接入力です。
    """.trimIndent()

    private fun twelveKeyGuide() = """
        【ガラケー入力（12キー）】
        P：かな → 英字 → 半角数字 → 全角数字・記号 → かな。QWERTYには移行しません。
        Pの長押しリピートでは連続切替しません。未確定文字は確定してから切り替えます。
        ${preferences.shortcut("language")?.label() ?: "未設定"}または画面のモードボタン：QWERTYを含む入力方式の切替。
        Ctrl+Space：AndroidのIME切替（OS・端末の設定に依存）。

        【左右の文字キー：常時両方有効】
        1/2/3：W/E/R または Y/U/I
        4/5/6：S/D/F または H/J/K
        7/8/9：Z/X/C または B/N/M
        0/*/#：G/T/V（共通）
        かな・英字はMozc標準循環。半角数字は毎回追加。全角数字は同じキーで数字・記号を循環。
        左右の対応キーを交互に押しても同じトークンです。時間による区切りはありません。

        【編集・変換】
        初期状態：Q＝左、A＝右、O＝上、L＝下。ガラケー入力設定で軸を入れ替えられます。
        右移動で同じ行の文字を区切ります。左右で途中挿入位置を変更、Backspaceで削除。
        Spaceでかなを漢字変換。変換中は上下で候補、左右で文節を操作。Enterで確定。
        確定後は通常の上下左右移動です。数字モードは編集中の文字を確定してから移動します。
        QWERTYにはこの配列やP切替を適用しません。修飾キー併用・直接入力欄は既存動作です。
    """.trimIndent()
}
