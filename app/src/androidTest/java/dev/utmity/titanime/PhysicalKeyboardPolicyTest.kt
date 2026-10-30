package dev.utmity.titanime

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

/** Real built-in KCM lookup, but constructed events: not physical key delivery. */
class PhysicalKeyboardPolicyTest {
    @Test fun titanAltLayerAndShortcutExclusions() {
        val device = InputDevice.getDevice(0)
        assertTrue("Test requires the observed TitanKey keyboard", PhysicalKeyboardPolicy.isTitanKeyboard(device))
        fun event(code: Int, meta: Int = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON, id: Int = 0) =
            KeyEvent(1, 2, KeyEvent.ACTION_DOWN, code, 0, meta, id, 0)
        for ((code, text) in listOf(KeyEvent.KEYCODE_R to '3', KeyEvent.KEYCODE_Q to '0',
            KeyEvent.KEYCODE_I to '-', KeyEvent.KEYCODE_N to ',', KeyEvent.KEYCODE_M to '.')) {
            val key = event(code)
            assertEquals(text.code, PhysicalKeyboardPolicy.altText(key, false, true))
            assertNull(PhysicalKeyboardPolicy.altText(key, true, true))
            assertNull(PhysicalKeyboardPolicy.altText(key, false, false))
        }
        assertNull(PhysicalKeyboardPolicy.altText(event(KeyEvent.KEYCODE_R, KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON), false, true))
        assertNull(PhysicalKeyboardPolicy.altText(event(KeyEvent.KEYCODE_R, KeyEvent.META_ALT_ON or KeyEvent.META_META_ON), false, true))
        assertNull(PhysicalKeyboardPolicy.altText(event(KeyEvent.KEYCODE_R, KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON), false, true))
        assertNull(PhysicalKeyboardPolicy.altText(event(KeyEvent.KEYCODE_SPACE), false, true))
        assertNull(PhysicalKeyboardPolicy.altText(event(KeyEvent.KEYCODE_R, 0), false, true))
        assertNull(PhysicalKeyboardPolicy.altText(event(KeyEvent.KEYCODE_R, id = -1), false, true))
    }
}
