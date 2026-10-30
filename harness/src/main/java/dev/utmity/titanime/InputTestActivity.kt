package dev.utmity.titanime

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only, isolated editor. Key metadata stays on screen; no typed text is logged. */
class InputTestActivity : Activity() {
    lateinit var editor: EditText
    lateinit var secondEditor: EditText
    private lateinit var events: TextView
    val receivedKeys = mutableListOf<KeyEvent>()
    var rewriteNextComposition: String? = null
    var staleSnapshotDuringBatch = false
    override fun onCreate(state: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        editor = object : EditText(this) {
            override fun onCreateInputConnection(info: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
                val original = super.onCreateInputConnection(info) ?: return null
                return object : android.view.inputmethod.InputConnectionWrapper(original, false) {
                    private var depth = 0
                    private var beforeBatch: android.view.inputmethod.ExtractedText? = null
                    override fun beginBatchEdit(): Boolean {
                        if (depth == 0 && staleSnapshotDuringBatch) {
                            val previous = super.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                            beforeBatch = previous?.let {
                                android.view.inputmethod.ExtractedText().apply {
                                    text = it.text?.toString(); startOffset = it.startOffset
                                    selectionStart = it.selectionStart; selectionEnd = it.selectionEnd
                                    partialStartOffset = it.partialStartOffset; partialEndOffset = it.partialEndOffset
                                    flags = it.flags
                                }
                            }
                        }
                        depth++
                        return super.beginBatchEdit()
                    }
                    override fun endBatchEdit(): Boolean {
                        depth = (depth - 1).coerceAtLeast(0)
                        val result = super.endBatchEdit()
                        if (depth == 0) beforeBatch = null
                        return result
                    }
                    override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? {
                        if (staleSnapshotDuringBatch && depth > 0) return beforeBatch
                        return super.getExtractedText(request, flags)
                    }
                    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        val replacement = rewriteNextComposition
                        rewriteNextComposition = null
                        return super.setComposingText(replacement ?: text, newCursorPosition)
                    }
                }
            }
        }.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Titan IME input test"
            minLines = 2
        }
        secondEditor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Second input field"
            minLines = 2
        }
        events = TextView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else view.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
            addView(editor)
            addView(secondEditor)
            addView(events)
        })
        editor.requestFocus()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        receivedKeys.add(KeyEvent(event))
        val line = "action=${event.action} key=${event.keyCode} scan=${event.scanCode} meta=${event.metaState} device=${event.deviceId} repeat=${event.repeatCount}"
        events.text = (events.text.toString() + "\n" + line).takeLast(4000)
        android.util.Log.i("TitanKeyProbe", line)
        return super.dispatchKeyEvent(event)
    }
}
