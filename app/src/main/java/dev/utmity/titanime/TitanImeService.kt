package dev.utmity.titanime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView

/**
 * Prototype physical-keyboard IME.
 * Ctrl+Shift+J toggles language internally. Ctrl+Space is reserved by Android
 * for switching input methods on the tested Titan device.
 */
class TitanImeService : InputMethodService() {
    private val composer = RomajiComposer()
    private var japanese = false
    private var strip: TextView? = null
    private val titanFnDevices = mutableSetOf<Int>()

    // Observed TitanKey mapping: vendor key 403 / Linux scan code 251.
    private fun isTitanFn(event: KeyEvent) = event.keyCode == 403 && event.scanCode == 251

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isTitanFn(event)) titanFnDevices.remove(event.deviceId)
        return super.onKeyUp(keyCode, event)
    }

    override fun onFinishInput() {
        titanFnDevices.clear()
        super.onFinishInput()
    }

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
        titanFnDevices.clear()
        refreshStrip()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            attribute?.packageName == "dev.utmity.titanime.harness") {
            sendBroadcast(android.content.Intent("dev.utmity.titanime.INPUT_READY")
                .setPackage("dev.utmity.titanime.harness"))
        }
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
        if (isTitanFn(event)) titanFnDevices.add(event.deviceId)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            currentInputEditorInfo?.packageName == "dev.utmity.titanime.harness") {
            android.util.Log.i("TitanImeProbe", "key=$keyCode meta=${event.metaState} japanese=$japanese connected=${currentInputConnection != null}")
        }
        val ic = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        if (event.isSymPressed || event.isFunctionPressed || event.deviceId in titanFnDevices) {
            if (japanese && composer.text().isNotEmpty()) {
                commitComposition(ic)
                // Keep commit and the following key ordered on the same connection.
                ic.sendKeyEvent(event)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_J && event.isCtrlPressed && event.isShiftPressed &&
            !event.isAltPressed && !event.isMetaPressed) {
            if (event.repeatCount > 0) return true
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
