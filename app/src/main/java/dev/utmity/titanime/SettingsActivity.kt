package dev.utmity.titanime

import android.app.Activity
import android.os.Bundle
import android.widget.*

/** Local preferences and a user-invoked system picker; never selects an IME itself. */
class SettingsActivity : Activity() {
    private lateinit var preferences: ImePreferences
    private lateinit var mode: TextView
    private lateinit var guide: TextView
    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        preferences = ImePreferences(this)
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
        var target = content
        fun text(value: String) = TextView(this).apply {
            text = value; textSize = 18f; setTextColor(0xff202124.toInt()); target.addView(this)
        }
        text("Titan IME 設定")
        text("Version 0.1.1-pr2 ・ build 2")
        mode = text("")
        target.addView(Button(this).apply {
            text = "入力方法を切り替える"
            setOnClickListener {
                getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker()
            }
        })
        val sections = linkedMapOf<String, LinearLayout>()
        for (title in listOf("入力・変換", "物理キー", "辞書・学習・履歴", "音声・自動入力", "操作ガイド")) {
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = android.view.View.GONE
                setPadding(12, 0, 12, 16)
            }
            val header = Button(this).apply {
                text = "＋ $title"
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setOnClickListener {
                    val opening = body.visibility != android.view.View.VISIBLE
                    body.visibility = if (opening) android.view.View.VISIBLE else android.view.View.GONE
                    text = (if (opening) "− " else "＋ ") + title
                }
            }
            content.addView(header); content.addView(body)
            sections[title] = body
        }
        target = sections.getValue("辞書・学習・履歴")
        target.addView(Button(this).apply { text = "ユーザー辞書"; setOnClickListener {
            startActivity(android.content.Intent(this@SettingsActivity, UserDictionaryActivity::class.java))
        } })
        fun choices(title: String, values: List<Pair<String, String>>, current: String, save: (String) -> Unit) {
            target = sections.getValue(when {
                title.startsWith("音声") -> "音声・自動入力"
                title.startsWith("履歴件数") || title.startsWith("クリップボード") || title.startsWith("端末内") -> "辞書・学習・履歴"
                title.startsWith("入力中") || title.startsWith("Mozc") || title.startsWith("候補バー") -> "入力・変換"
                else -> "物理キー"
            })
            text(title)
            val group = RadioGroup(this)
            val ids = mutableMapOf<Int, String>()
            values.forEach { (value, label) ->
                val button = RadioButton(this).apply { id = android.view.View.generateViewId(); text = label }
                ids[button.id] = value
                group.addView(button)
                if (value == current) group.check(button.id)
            }
            group.setOnCheckedChangeListener { _, id -> ids[id]?.let(save) }
            target.addView(group)
        }
        target = sections.getValue("物理キー")
        text("ショートカット")
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
                            if (::guide.isInitialized) guide.text = guideText()
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
                            binding.reserved() -> "この組み合わせは文節操作・カーソル編集用に予約されています。"
                            else -> preferences.shortcuts().ownerOf(binding, action)?.let {
                                "「${when (it) { "language" -> "日英切替"; "history" -> "履歴"; else -> "文字変換" }}」で使用中です。"
                            }
                        }
                        if (reason != null) hint.text = reason
                        else {
                            preferences.setShortcut(action, binding)
                            button.text = description()
                            if (::guide.isInitialized) guide.text = guideText()
                            dialog.dismiss()
                        }
                        true
                    }
                }
                dialog.show()
            }
            target.addView(button)
        }
        shortcutButton("language", "日英切替")
        shortcutButton("history", "クリップボード履歴")
        shortcutButton("transform", "選択文字の変換")
        choices("候補バーの表示", listOf("always" to "通常時も表示", "compose" to "日本語入力・変換中のみ表示", "conversion" to "変換中のみ表示"), preferences.toolbarMode) { preferences.toolbarMode = it }
        target = sections.getValue("入力・変換")
        text("バーを隠している間も物理キーボードで入力できます。変換中やメニュー表示中は必要に応じてバーが現れます。")
        choices("T2E内蔵キーボードのAlt", listOf("text" to "Altで数字・記号（標準）", "meta" to "Altをアプリへ渡す（Meta）"), if (preferences.altText) "text" else "meta") { preferences.altText = it == "text" }
        choices("通常編集のカーソル操作", listOf("on" to "Fn+H/L/K/J：左/右/上/下", "off" to "無効（アプリへ渡す）"), if (preferences.editing) "on" else "off") { preferences.editing = it == "on" }
        text("Shift併用：範囲選択、Ctrl併用：左右の単語移動。変換中は従来の文節操作を優先。上下・単語移動時は未確定を確定します。直接入力欄は対象外です。")
        choices("音声認識の優先言語", listOf("auto" to "自動（端末の言語を基準）", "ja" to "日本語優先", "en" to "英語優先"), preferences.voiceLanguage) { preferences.voiceLanguage = it }
        text("対応サービスでは日英を自動切替。非対応時は優先言語で認識します。音声が認識サービスへ送信される場合があります。")
        choices("通常編集の方向キー配置", listOf("hjkl" to "H/L/K/J：左/右/上/下", "wasd" to "A/D/W/S：左/右/上/下"), preferences.editingLayout) { preferences.editingLayout = it }
        choices("単語削除", listOf("off" to "無効（標準）", "on" to "Fn+BackspaceをCtrl+Backspaceとして送る"), if (preferences.wordDelete) "on" else "off") { preferences.wordDelete = it == "on" }
        val history = ClipboardHistory(this)
        choices("クリップボード履歴（端末内保存）", listOf("off" to "保存しない（標準）", "on" to "保存する"), if (history.enabled) "on" else "off") { history.enabled = it == "on" }
        choices("履歴件数（ピン留めを含む）", listOf("10" to "10件", "20" to "20件", "50" to "50件"), history.limit.toString()) { history.limit = it.toInt() }
        text("有効な通常入力欄がある間の新しいコピーのみ保存。機密フラグ付き・パスワード欄・URI・1万文字超は保存しません。機密フラグのない秘密情報は識別できないため、その場合は保存を無効にしてください。オフにしても保存済み履歴は残ります。")
        target.addView(Button(this).apply { text = "全履歴を削除…"; setOnClickListener {
            android.app.AlertDialog.Builder(this@SettingsActivity).setMessage("ピン留めを含む保存済み履歴を削除しますか？")
                .setNegativeButton("キャンセル", null).setPositiveButton("削除") { _, _ -> history.clear() }.show()
        } })
        target = sections.getValue("音声・自動入力")
        target.addView(Button(this).apply { text = "Androidの自動入力設定"; setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(android.net.Uri.parse("package:$packageName"))
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        } })
        choices("文節操作（変換中のみ）", listOf("hl" to "Fn+H/L：前/次・Fn+U/O：縮小/拡大", "jk" to "Fn+J/K：前/次・Fn+U/O：縮小/拡大"), preferences.segments) { preferences.segments = it }
        choices("端末内の変換学習", listOf("on" to "変換学習オン", "off" to "変換学習オフ"), if (preferences.learning) "on" else "off") { preferences.learning = it == "on" }
        choices("入力中の予測候補", listOf("on" to "予測変換オン", "off" to "予測変換オフ"), if (preferences.prediction) "on" else "off") { preferences.prediction = it == "on" }
        choices("Mozc標準の訂正候補（自動置換なし）", listOf("on" to "訂正候補オン", "off" to "訂正候補オフ"), if (preferences.correction) "on" else "off") { preferences.correction = it == "on" }
        text("訂正オンでは濁点・半濁点・小書きかなの違いも候補検索に含めます。例：かつこう→学校。元の読みは「そのまま」で確定できます。一般的な脱字や隣接キーの打ち間違いには未対応です。")
        target = sections.getValue("操作ガイド")
        guide = text(guideText())
        fun styleButtons(view: android.view.View) {
            if (view is Button && view !is CompoundButton) {
                view.setTextColor(0xff202124.toInt())
                view.backgroundTintList = android.content.res.ColorStateList.valueOf(0xfff1f3f4.toInt())
                view.elevation = 0f
                view.stateListAnimator = null
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) styleButtons(view.getChildAt(i))
        }
        styleButtons(content)
        setContentView(ScrollView(this).apply { setBackgroundColor(android.graphics.Color.WHITE); addView(content) })
    }
    override fun onResume() {
        super.onResume()
        mode.text = "現在の入力モード：" + if (preferences.japanese) "日本語" else "英語"
        if (::guide.isInitialized) guide.text = guideText()
    }

    private fun guideText(): String = """
        【入力】
        ${preferences.shortcut("language")?.label() ?: "未設定"}：日本語・英語切替
        日本語入力中はSpaceで変換・候補送り、Enterで確定します。
        変換候補には元のローマ字も表示できます。タップすると入力した英字で確定します。
        Backspace：削除。- , .：ー 、 。を入力します。

        【物理キー】
        ${preferences.shortcut("history")?.label() ?: "未設定"}：クリップボード履歴
        ${preferences.shortcut("transform")?.label() ?: "未設定"}：選択文字の変換
        文節移動：${if (preferences.segments == "jk") "Fn+J/K" else "Fn+H/L"}
        文節長変更：Fn+U/O。通常のカーソル編集：Fn＋方向キー配置。
        「物理キー」のショートカットをタップして別の組み合わせに変更できます。
        Androidや入力先アプリに先取りされる組み合わせは使用できない場合があります。

        【入力バーとツール】
        「入力・変換」で通常時・入力中・変換中の表示条件を変更できます。
        バーの「あ/A」でモード変更、マイクで音声入力、…で履歴・文字変換・Undo/Redo・自動入力・IME切替ができます。
        バーを隠した場合、これらの画面操作にはバーを再表示する必要があります。

        【辞書・プライバシー】
        ユーザー辞書は編集・インポート・エクスポートできます。
        変換学習は端末内。オフにしても既存の学習履歴は消えません。
        クリップボード履歴は任意の保存機能で、パスワード欄では利用しません。
        音声認識は端末のサービスが処理し、音声が外部へ送信される場合があります。

        【制限】
        パスワード・数字入力欄や一部の特殊な入力欄では日本語変換しません。
        システムのナビゲーションバーはTitan IMEの候補バーとは別です。
    """.trimIndent()
}
