package dev.utmity.titanime

import android.view.KeyEvent

/** Translation only; layout, graphemes, selection anchors and word breaks belong to the editor. */
internal object EditingBindings {
    fun direction(event: KeyEvent, fn: Boolean, enabled: Boolean, layout: String = "hjkl"): Int? {
        if (!enabled || !fn || event.isAltPressed || event.isMetaPressed || event.isSymPressed) return null
        if (layout == "wasd") return when (event.keyCode) {
            KeyEvent.KEYCODE_A -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_D -> KeyEvent.KEYCODE_DPAD_RIGHT
            KeyEvent.KEYCODE_W -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_S -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> null
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_H -> KeyEvent.KEYCODE_DPAD_LEFT
            KeyEvent.KEYCODE_L -> KeyEvent.KEYCODE_DPAD_RIGHT
            KeyEvent.KEYCODE_K -> KeyEvent.KEYCODE_DPAD_UP
            KeyEvent.KEYCODE_J -> KeyEvent.KEYCODE_DPAD_DOWN
            else -> null
        }
    }
    fun send(connection: android.view.inputmethod.InputConnection, code: Int, source: KeyEvent) {
        // TextView's selection extension can depend on a Shift span in its
        // Editable rather than only the synthetic event's metaState. Keep the
        // anchor explicitly for horizontal selection when a full snapshot exists.
        if (source.isShiftPressed && !source.isCtrlPressed && code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val snapshot = connection.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            val text = snapshot?.text?.toString()
            if (snapshot != null && text != null && snapshot.partialStartOffset < 0 &&
                snapshot.selectionStart in 0..text.length && snapshot.selectionEnd in 0..text.length) {
                val boundaries = android.icu.text.BreakIterator.getCharacterInstance()
                boundaries.setText(text)
                val next = if (code == KeyEvent.KEYCODE_DPAD_LEFT) boundaries.preceding(snapshot.selectionEnd) else boundaries.following(snapshot.selectionEnd)
                val end = if (next == android.icu.text.BreakIterator.DONE) snapshot.selectionEnd else next
                if (connection.setSelection(snapshot.startOffset + snapshot.selectionStart, snapshot.startOffset + end)) return
            }
        }
        val meta = source.metaState and KeyEvent.META_FUNCTION_ON.inv()
        val now = android.os.SystemClock.uptimeMillis()
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }
}
