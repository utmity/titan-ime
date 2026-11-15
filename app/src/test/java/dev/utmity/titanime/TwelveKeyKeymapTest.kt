package dev.utmity.titanime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TwelveKeyKeymapTest {
    private fun character(value: Char) = TwelveKeyKeymap.Action.Character(value)

    @Test fun leftHandMappingMatchesPhysicalKeys() {
        val codes = listOf(
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C,
        )
        codes.forEachIndexed { index, code ->
            assertEquals(character('1' + index), TwelveKeyKeymap.translate(code, TwelveKeyKeymap.Hand.LEFT))
        }
        assertEquals(TwelveKeyKeymap.Action.MoveLeft, TwelveKeyKeymap.translate(KeyEvent.KEYCODE_Q, TwelveKeyKeymap.Hand.LEFT))
        assertEquals(TwelveKeyKeymap.Action.MoveRight, TwelveKeyKeymap.translate(KeyEvent.KEYCODE_A, TwelveKeyKeymap.Hand.LEFT))
        assertNull(TwelveKeyKeymap.translate(KeyEvent.KEYCODE_Y, TwelveKeyKeymap.Hand.LEFT))
    }

    @Test fun rightHandMappingMatchesPhysicalKeys() {
        val codes = listOf(
            KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M,
        )
        codes.forEachIndexed { index, code ->
            assertEquals(character('1' + index), TwelveKeyKeymap.translate(code, TwelveKeyKeymap.Hand.RIGHT))
        }
        assertEquals(TwelveKeyKeymap.Action.MoveLeft, TwelveKeyKeymap.translate(KeyEvent.KEYCODE_O, TwelveKeyKeymap.Hand.RIGHT))
        assertEquals(TwelveKeyKeymap.Action.MoveRight, TwelveKeyKeymap.translate(KeyEvent.KEYCODE_L, TwelveKeyKeymap.Hand.RIGHT))
        assertNull(TwelveKeyKeymap.translate(KeyEvent.KEYCODE_W, TwelveKeyKeymap.Hand.RIGHT))
    }

    @Test fun modifierSymbolAndWaKeysAreCommon() {
        for (hand in TwelveKeyKeymap.Hand.entries) {
            assertEquals(character('0'), TwelveKeyKeymap.translate(KeyEvent.KEYCODE_G, hand))
            assertEquals(character('*'), TwelveKeyKeymap.translate(KeyEvent.KEYCODE_T, hand))
            assertEquals(character('#'), TwelveKeyKeymap.translate(KeyEvent.KEYCODE_V, hand))
            assertNull(TwelveKeyKeymap.translate(KeyEvent.KEYCODE_SPACE, hand))
            assertNull(TwelveKeyKeymap.translate(KeyEvent.KEYCODE_DEL, hand))
            assertNull(TwelveKeyKeymap.translate(KeyEvent.KEYCODE_SYM, hand))
        }
    }
}
