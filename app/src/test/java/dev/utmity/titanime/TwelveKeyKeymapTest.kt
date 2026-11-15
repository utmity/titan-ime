package dev.utmity.titanime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwelveKeyKeymapTest {
    @Test fun bothHalvesAlwaysProduceSameTokensWithEitherCursorSetting() {
        val left = listOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C)
        val right = listOf(KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M)
        for (swapped in listOf(false, true)) {
            left.zip(right).forEachIndexed { index, (l, r) ->
                val expected = TwelveKeyKeymap.Action.Character('1' + index)
                assertEquals(expected, TwelveKeyKeymap.translate(l, swapped))
                assertEquals(expected, TwelveKeyKeymap.translate(r, swapped))
            }
            for ((code, token) in listOf(KeyEvent.KEYCODE_G to '0', KeyEvent.KEYCODE_T to '*', KeyEvent.KEYCODE_V to '#')) {
                assertEquals(TwelveKeyKeymap.Action.Character(token), TwelveKeyKeymap.translate(code, swapped))
            }
            for (code in listOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_SYM,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
                assertNull(TwelveKeyKeymap.translate(code, swapped))
            }
        }
    }

    @Test fun pIsModeActionWithEitherCursorSetting() {
        assertEquals(TwelveKeyKeymap.Action.NextMode, TwelveKeyKeymap.translate(KeyEvent.KEYCODE_P))
        assertEquals(TwelveKeyKeymap.Action.NextMode, TwelveKeyKeymap.translate(KeyEvent.KEYCODE_P, true))
    }

    @Test fun defaultAndSwappedCursorAxes() {
        val codes = listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_L)
        val defaults = listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        for ((code, direction) in codes.zip(defaults)) {
            assertEquals(TwelveKeyKeymap.Action.Navigate(direction), TwelveKeyKeymap.translate(code))
        }
        val swapped = listOf(defaults[2], defaults[3], defaults[0], defaults[1])
        for ((code, direction) in codes.zip(swapped)) {
            assertEquals(TwelveKeyKeymap.Action.Navigate(direction), TwelveKeyKeymap.translate(code, true))
        }
    }
}
