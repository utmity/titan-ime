package dev.utmity.titanime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView

/**
 * Prototype physical-keyboard IME.
 * Ctrl+Space toggles language internally; this shortcut may be intercepted by
 * Android before delivery on some devices, so hardware testing is required.
 */
class TitanImeService : InputMethodService() {
    private val composer = RomajiComposer()
    private var japanese = false
    private var strip: TextView? = null

    override fun onCreateInputView(): View {
        return TextView(this).apply {
            textSize = 14f
            setPadding(12, 6, 12, 6)
            strip = this
            refreshStrip()
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composer.clear()
        refreshStrip()
    }

    private fun refreshStrip() {
        strip?.text = if (japanese) "あ  " + composer.text() else "A  Direct input"
    }

    private fun replaceComposing(ic: InputConnection) {
        ic.setComposingText(composer.text(), 1)
        refreshStrip()
    }

    private fun commitComposition(ic: InputConnection) {
        composer.flush()
        if (composer.text().isNotEmpty()) ic.commitText(composer.text(), 1)
        else ic.finishComposingText()
        composer.clear()
        refreshStrip()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ic = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.isCtrlPressed) {
            if (japanese) commitComposition(ic)
            japanese = !japanese
            refreshStrip()
            return true
        }
        if (!japanese || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) {
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (composer.text().isNotEmpty()) {
                    composer.backspace()
                    replaceComposing(ic)
                    if (composer.text().isEmpty()) ic.finishComposingText()
                    refreshStrip()
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                commitComposition(ic)
                if (keyCode == KeyEvent.KEYCODE_SPACE) ic.commitText(" ", 1)
                else ic.sendKeyEvent(event)
                return true
            }
        }
        val unicode = event.unicodeChar
        if (unicode > 0) {
            val c = unicode.toChar()
            if (c.isLetter() && c.code < 128) {
                composer.type(c)
                replaceComposing(ic)
                return true
            }
            if (composer.text().isNotEmpty()) commitComposition(ic)
        }
        return super.onKeyDown(keyCode, event)
    }
}
