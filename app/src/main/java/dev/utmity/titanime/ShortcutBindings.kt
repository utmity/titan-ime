package dev.utmity.titanime

import android.view.KeyEvent

/** Persist only key codes and modifier masks; comparisons do not require storage access. */
internal data class ShortcutBinding(val keyCode: Int, val modifiers: Int) {
    fun matches(event: KeyEvent, fnHeld: Boolean): Boolean =
        keyCode == event.keyCode && modifiers == modifierMask(event, fnHeld)

    fun encode(): String = "$keyCode:$modifiers"

    fun label(): String {
        val parts = mutableListOf<String>()
        if (modifiers and CTRL != 0) parts += "Ctrl"
        if (modifiers and ALT != 0) parts += "Alt"
        if (modifiers and SHIFT != 0) parts += "Shift"
        if (modifiers and FN != 0) parts += "Fn"
        if (modifiers and SYM != 0) parts += "Sym"
        if (modifiers and META != 0) parts += "Meta"
        val key = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        parts += when (key) {
            "DEL" -> "Backspace"
            "ENTER" -> "Enter"
            else -> key
        }
        return parts.joinToString(" + ")
    }

    /** Keys reserved for IME composition/editing cannot be rebound safely. */
    fun reserved(): Boolean = (modifiers == FN && keyCode in setOf(
        KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K,
        KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DEL))

    companion object {
        const val FN = 1
        const val CTRL = 2
        const val SHIFT = 4
        const val ALT = 8
        const val SYM = 16
        const val META = 32
        private val ALLOWED = FN or CTRL or SHIFT or ALT or SYM or META

        fun modifierMask(event: KeyEvent, fnHeld: Boolean): Int {
            var value = 0
            if (fnHeld) value = value or FN
            if (event.isCtrlPressed) value = value or CTRL
            if (event.isShiftPressed) value = value or SHIFT
            if (event.isAltPressed) value = value or ALT
            if (event.isSymPressed) value = value or SYM
            if (event.isMetaPressed) value = value or META
            return value
        }

        fun capture(event: KeyEvent, fnHeld: Boolean): ShortcutBinding? {
            if (event.keyCode <= 0 || event.keyCode > KeyEvent.getMaxKeyCode() ||
                KeyEvent.isModifierKey(event.keyCode) ||
                event.keyCode in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_FUNCTION)) return null
            val modifiers = modifierMask(event, fnHeld)
            // Don't steal plain text or uppercase letters from ordinary typing.
            if (modifiers == 0 || modifiers == SHIFT) return null
            return ShortcutBinding(event.keyCode, modifiers)
        }

        fun decode(saved: String?): ShortcutBinding? {
            if (saved == null || saved == "off") return null
            val pair = saved.split(':')
            if (pair.size != 2) return null
            val key = pair[0].toIntOrNull() ?: return null
            val mods = pair[1].toIntOrNull() ?: return null
            if (key <= 0 || key > KeyEvent.getMaxKeyCode() || mods == 0 ||
                mods == SHIFT || mods and ALLOWED != mods) return null
            return ShortcutBinding(key, mods)
        }
    }
}

internal data class ShortcutProfile(
    val language: ShortcutBinding?,
    val history: ShortcutBinding?,
    val transform: ShortcutBinding?
) {
    fun ownerOf(binding: ShortcutBinding, excluding: String): String? = listOf(
        "language" to language, "history" to history, "transform" to transform)
        .firstOrNull { it.first != excluding && it.second == binding }?.first
}
