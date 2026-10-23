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
    private lateinit var events: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            hint = "Titan IME input test"
            minLines = 4
        }
        events = TextView(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(editor)
            addView(events)
        })
        editor.requestFocus()
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val line = "action=${event.action} key=${event.keyCode} scan=${event.scanCode} meta=${event.metaState} device=${event.deviceId} repeat=${event.repeatCount}"
        events.text = (events.text.toString() + "\n" + line).takeLast(4000)
        android.util.Log.i("TitanKeyProbe", line)
        return super.dispatchKeyEvent(event)
    }
}
