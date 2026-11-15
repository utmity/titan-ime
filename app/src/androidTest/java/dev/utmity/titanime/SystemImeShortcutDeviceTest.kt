package dev.utmity.titanime

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class SystemImeShortcutDeviceTest {
    @Test fun osChordMatchesCtrlSpaceButNotApplicationModeKeys() {
        fun key(code: Int, meta: Int) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)
        assertTrue(SystemImeShortcut.matches(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), false))
        assertTrue(SystemImeShortcut.matches(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON), false))
        assertFalse(SystemImeShortcut.matches(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON), true))
        assertFalse(SystemImeShortcut.matches(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON), false))
        assertFalse(SystemImeShortcut.matches(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_FUNCTION_ON), true))
        assertFalse(SystemImeShortcut.matches(key(KeyEvent.KEYCODE_P, 0), false))
    }
}
