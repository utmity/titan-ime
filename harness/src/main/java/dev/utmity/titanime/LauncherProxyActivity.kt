package dev.utmity.titanime

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout

/** Simulates a launcher proxy copying its first input into a real search editor. */
class LauncherProxyActivity : Activity() {
    lateinit var proxy: EditText
    lateinit var search: EditText
    val appKeys = mutableListOf<KeyEvent>()
    var switched = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        search = EditText(this).apply { id = android.view.View.generateViewId(); inputType = InputType.TYPE_CLASS_TEXT; hint = "Search fixture" }
        proxy = object : EditText(this) {
            override fun onCreateInputConnection(info: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
                val original = super.onCreateInputConnection(info) ?: return null
                return object : InputConnectionWrapper(original, false) {
                    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        val result = super.setComposingText(text, newCursorPosition)
                        if (!switched) {
                            switched = true
                            val copied = this@LauncherProxyActivity.proxy.text.toString()
                            post {
                                layout.addView(search)
                                search.requestFocus()
                                search.postDelayed({
                                    search.setText(copied); search.setSelection(copied.length)
                                    getSystemService(InputMethodManager::class.java).restartInput(search)
                                    getSystemService(InputMethodManager::class.java).showSoftInput(search, 0)
                                }, intent.getLongExtra("copy_delay", 100))
                            }
                        }
                        return result
                    }
                }
            }
        }.apply {
            id = android.view.View.generateViewId()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(intent.getStringExtra("prefix") ?: "")
            setSelection(text.length)
        }
        layout.addView(proxy, LinearLayout.LayoutParams(1, 1))
        setContentView(layout)
        proxy.requestFocus()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        appKeys.add(KeyEvent(event))
        return super.dispatchKeyEvent(event)
    }
}
