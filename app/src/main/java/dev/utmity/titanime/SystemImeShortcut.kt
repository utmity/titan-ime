package dev.utmity.titanime

import android.view.KeyEvent

/** Ctrl+Space (and reverse Shift+Ctrl+Space) belongs to Android. */
internal object SystemImeShortcut {
    fun matches(event: KeyEvent, fnHeld: Boolean): Boolean = event.keyCode == KeyEvent.KEYCODE_SPACE &&
        event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed && !event.isSymPressed && !fnHeld

    fun reserved(binding: ShortcutBinding): Boolean = binding.keyCode == KeyEvent.KEYCODE_SPACE &&
        (binding.modifiers == ShortcutBinding.CTRL || binding.modifiers == (ShortcutBinding.CTRL or ShortcutBinding.SHIFT))
}
