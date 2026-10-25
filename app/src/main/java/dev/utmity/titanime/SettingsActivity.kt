package dev.utmity.titanime

import android.app.Activity
import android.os.Bundle
import android.widget.*

/** Local preferences only; no IME selection or system settings are changed. */
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
        choices("文節操作（変換中のみ）", listOf("hl" to "Fn+H/L：前/次・Shift併用：縮小/拡大", "jk" to "Fn+J/K：前/次・Shift併用：縮小/拡大"), preferences.segments) { preferences.segments = it }
        text("Space：変換・次候補\nEnter：確定（再度で改行）\nBackspace：削除\n記号 - , . → ー 、 。\n文節長変更はFnとShiftを先に押します。\nCtrl+Shift+Jは廃止しました。\n端末独自のショートカットが優先される場合は別の切替を選んでください。\nパスワード・数字欄は直接入力です。")
        setContentView(ScrollView(this).apply { addView(content) })
    }
    override fun onResume() {
        super.onResume()
        mode.text = "現在の入力モード：" + if (preferences.japanese) "日本語" else "英語"
    }
}
