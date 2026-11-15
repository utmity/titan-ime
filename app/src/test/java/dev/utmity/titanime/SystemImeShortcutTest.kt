package dev.utmity.titanime

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class SystemImeShortcutTest {
    @Test fun reserveOnlyAndroidImeSwitchChordsNotFnSpace() {
        assertTrue(SystemImeShortcut.reserved(ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.CTRL)))
        assertTrue(SystemImeShortcut.reserved(ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.CTRL or ShortcutBinding.SHIFT)))
        assertFalse(SystemImeShortcut.reserved(ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.FN)))
        assertFalse(SystemImeShortcut.reserved(ShortcutBinding(KeyEvent.KEYCODE_P, ShortcutBinding.CTRL)))
        assertFalse(SystemImeShortcut.reserved(ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.CTRL or ShortcutBinding.ALT)))
    }
}
