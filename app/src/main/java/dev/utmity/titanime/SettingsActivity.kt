package dev.utmity.titanime

import android.app.Activity
import android.os.Bundle
import android.widget.*

/** Local preferences and a user-invoked system picker; never selects an IME itself. */
class SettingsActivity : Activity() {
    private lateinit var preferences: ImePreferences
    private lateinit var mode: TextView
    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        preferences = ImePreferences(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
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
        fun text(value: String) = TextView(this).apply { text = value; textSize = 18f; content.addView(this) }
        text("Titan IME 設定")
        mode = text("")
        content.addView(Button(this).apply {
            text = "入力方法を切り替える"
            setOnClickListener {
                getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker()
            }
        })
        content.addView(Button(this).apply { text = "ユーザー辞書"; setOnClickListener {
            startActivity(android.content.Intent(this@SettingsActivity, UserDictionaryActivity::class.java))
        } })
        fun choices(title: String, values: List<Pair<String, String>>, current: String, save: (String) -> Unit) {
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
            content.addView(group)
        }
        choices("日英切り替え（保存後すぐ反映）", listOf("fn" to "Fn+Space（標準）", "sym" to "Sym+Space", "alt" to "Alt+Space"), preferences.toggle) { preferences.toggle = it }
        choices("T2E内蔵キーボードのAlt", listOf("text" to "Altで数字・記号（標準）", "meta" to "Altをアプリへ渡す（Meta）"), if (preferences.altText) "text" else "meta") { preferences.altText = it == "text" }
        choices("通常編集のカーソル操作", listOf("on" to "Fn+H/L/K/J：左/右/上/下", "off" to "無効（アプリへ渡す）"), if (preferences.editing) "on" else "off") { preferences.editing = it == "on" }
        text("Shift併用：範囲選択、Ctrl併用：左右の単語移動。変換中は従来の文節操作を優先。上下・単語移動時は未確定を確定します。直接入力欄は対象外です。")
        choices("音声認識の優先言語", listOf("auto" to "自動（端末の言語を基準）", "ja" to "日本語優先", "en" to "英語優先"), preferences.voiceLanguage) { preferences.voiceLanguage = it }
        text("対応サービスでは日英を自動切替。非対応時は優先言語で認識します。音声が認識サービスへ送信される場合があります。")
        choices("文字変換", listOf("t" to "Fn+T", "g" to "Fn+G", "off" to "無効"), preferences.transformKey) { preferences.transformKey = it }
        choices("履歴呼び出し", listOf("v" to "Fn+V", "b" to "Fn+B", "off" to "キー割り当てなし"), preferences.clipboardKey) { preferences.clipboardKey = it }
        choices("通常編集の方向キー配置", listOf("hjkl" to "H/L/K/J：左/右/上/下", "wasd" to "A/D/W/S：左/右/上/下"), preferences.editingLayout) { preferences.editingLayout = it }
        choices("単語削除", listOf("off" to "無効（標準）", "on" to "Fn+BackspaceをCtrl+Backspaceとして送る"), if (preferences.wordDelete) "on" else "off") { preferences.wordDelete = it == "on" }
        val history = ClipboardHistory(this)
        choices("クリップボード履歴（端末内保存）", listOf("off" to "保存しない（標準）", "on" to "保存する"), if (history.enabled) "on" else "off") { history.enabled = it == "on" }
        choices("履歴件数（ピン留めを含む）", listOf("10" to "10件", "20" to "20件", "50" to "50件"), history.limit.toString()) { history.limit = it.toInt() }
        text("有効な通常入力欄がある間の新しいコピーのみ保存。機密フラグ付き・パスワード欄・URI・1万文字超は保存しません。機密フラグのない秘密情報は識別できないため、その場合は保存を無効にしてください。オフにしても保存済み履歴は残ります。")
        content.addView(Button(this).apply { text = "全履歴を削除…"; setOnClickListener {
            android.app.AlertDialog.Builder(this@SettingsActivity).setMessage("ピン留めを含む保存済み履歴を削除しますか？")
                .setNegativeButton("キャンセル", null).setPositiveButton("削除") { _, _ -> history.clear() }.show()
        } })
        content.addView(Button(this).apply { text = "Androidの自動入力設定"; setOnClickListener {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                .setData(android.net.Uri.parse("package:$packageName"))
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        } })
        choices("文節操作（変換中のみ）", listOf("hl" to "Fn+H/L：前/次・Fn+U/O：縮小/拡大", "jk" to "Fn+J/K：前/次・Fn+U/O：縮小/拡大"), preferences.segments) { preferences.segments = it }
        choices("端末内の変換学習", listOf("on" to "変換学習オン", "off" to "変換学習オフ"), if (preferences.learning) "on" else "off") { preferences.learning = it == "on" }
        choices("入力中の予測候補", listOf("on" to "予測変換オン", "off" to "予測変換オフ"), if (preferences.prediction) "on" else "off") { preferences.prediction = it == "on" }
        choices("Mozc標準の訂正候補（自動置換なし）", listOf("on" to "訂正候補オン", "off" to "訂正候補オフ"), if (preferences.correction) "on" else "off") { preferences.correction = it == "on" }
        text("訂正オンでは濁点・半濁点・小書きかなの違いも候補検索に含めます。例：かつこう→学校。元の読みは「そのまま」で確定できます。一般的な脱字や隣接キーの打ち間違いには未対応です。")
        text("Space：変換・次候補\nEnter：確定（再度で改行）\nBackspace：削除\n記号 - , . → ー 、 。\n文節長変更はFn+U（縮小）/Fn+O（拡大）。\nCtrl+Shift+Jは廃止しました。\n端末独自のショートカットが優先される場合は別の切替を選んでください。\nパスワード・数字欄は直接入力です。\n学習は端末内のみ。オフでも保存済み履歴は消しません。")
        setContentView(ScrollView(this).apply { addView(content) })
    }
    override fun onResume() {
        super.onResume()
        mode.text = "現在の入力モード：" + if (preferences.japanese) "日本語" else "英語"
    }
}
