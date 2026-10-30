package dev.utmity.titanime

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*

class UserDictionaryActivity : Activity() {
    private lateinit var store: UserDictionaryStore
    private lateinit var rows: LinearLayout
    private lateinit var search: EditText
    private val engine by lazy { MozcEngine(this) }
    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        store = UserDictionaryStore(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 48)
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    view.setPadding(24 + bars.left, 24 + bars.top, 24 + bars.right, 24 + bars.bottom)
                }; insets
            }
        }
        content.addView(TextView(this).apply { text = "ユーザー辞書"; textSize = 24f })
        content.addView(TextView(this).apply { text = "端末内のMozc辞書。保存後の入力から反映します。学習履歴とは別に保存します。" })
        fun button(label: String, action: () -> Unit) { content.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("単語を登録") { edit(null) }
        button("エクスポート（UTF-8 TSV）") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/tab-separated-values")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "titan-dictionary.tsv"), 1)
        }
        button("インポート（UTF-8 TSV）") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE), 2)
        }
        search = EditText(this).apply { hint = "単語・読みを検索"; setSingleLine() }
        content.addView(search)
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(rows)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: Editable?) {}
        })
        setContentView(ScrollView(this).apply { addView(content) })
        refresh()
    }
    private fun safely(action: () -> Unit) {
        try { action() } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("操作できませんでした").setMessage(error.message ?: "辞書を確認してください。")
                .setPositiveButton("閉じる", null).show()
        }
    }
    private fun refresh() = safely {
        rows.removeAllViews()
        val query = search.text.toString()
        val items = store.list().filter { it.entry.key.contains(query) || it.entry.value.contains(query) }
        rows.addView(TextView(this).apply { text = "${items.size}件（最大100件表示。検索で絞り込み）" })
        items.take(100).forEach { item ->
            rows.addView(Button(this).apply {
                isAllCaps = false
                text = "${item.entry.value} / ${item.entry.key} / ${UserDictionaryStore.positions[item.entry.pos] ?: item.entry.pos.name}"
                setOnClickListener { edit(item) }
            })
        }
    }
    private fun changed() {
        engine.reloadDictionary()
        refresh()
        Toast.makeText(this, "保存しました。次の入力から反映します。", Toast.LENGTH_SHORT).show()
    }
    private fun edit(item: UserDictionaryStore.Item?) {
        if (item != null && item.entry.pos !in UserDictionaryStore.positions) {
            AlertDialog.Builder(this).setMessage("この品詞の編集は未対応です。登録内容は保持しています。")
                .setPositiveButton("閉じる", null).show()
            return
        }
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        val word = EditText(this).apply { hint = "単語"; setSingleLine(); setText(item?.entry?.value ?: "") }
        val reading = EditText(this).apply { hint = "読み（ひらがな）"; setSingleLine(); setText(item?.entry?.key ?: "") }
        val positions = UserDictionaryStore.positions
        val pos = Spinner(this).apply {
            adapter = ArrayAdapter(this@UserDictionaryActivity, android.R.layout.simple_spinner_dropdown_item, positions.values.toList())
            setSelection(positions.keys.indexOf(item?.entry?.pos).coerceAtLeast(0))
        }
        form.addView(word); form.addView(reading); form.addView(pos)
        val dialog = AlertDialog.Builder(this).setTitle(if (item == null) "単語登録" else "登録内容の編集")
            .setView(form).setPositiveButton("保存", null).setNegativeButton("キャンセル", null)
        if (item != null) dialog.setNeutralButton("削除") { _, _ ->
            AlertDialog.Builder(this).setMessage("「${item.entry.value}」を辞書から削除しますか？学習履歴は削除しません。")
                .setNegativeButton("キャンセル", null).setPositiveButton("削除") { _, _ -> safely { store.delete(item); changed() } }.show()
        }
        val shown = dialog.create()
        shown.setOnShowListener { shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            safely {
                val entry = UserDictionaryStore.entry(reading.text.toString(), word.text.toString(), positions.keys.elementAt(pos.selectedItemPosition))
                store.put(entry, item); changed(); shown.dismiss()
            }
        } }
        shown.show()
    }
    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        safely {
            if (requestCode == 1) {
                contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use { it.write(store.exportText()) }
                Toast.makeText(this, "書き出しました", Toast.LENGTH_SHORT).show()
            } else if (requestCode == 2) {
                val bytes = contentResolver.openInputStream(uri)!!.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (buffer.size() <= 2_000_000) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        buffer.write(chunk, 0, count)
                    }
                    buffer.toByteArray()
                }
                require(bytes.size <= 2_000_000) { "ファイルは2MB以内にしてください。" }
                val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                val count = store.importText(text); changed()
                Toast.makeText(this, "${count}件を追加しました（重複はスキップ）", Toast.LENGTH_LONG).show()
            }
        }
    }
}
