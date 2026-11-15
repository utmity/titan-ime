package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*

/** Native tests of the same MozcEngine configuration used by Preview.
 * Run with -PandroidTestBuildType=preview on an arm64 device.
 */
class TwelveKeyMozcProbeTest {
    @Test fun nativeTwelveKeyTableToggleModifierCursorAndConversion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = MozcEngine(context).also { it.setTwelveKeyInput(true) }
        fun press(character: Char): Output = engine.key(KeyEvent.newBuilder().setKeyCode(character.code).build())
        fun special(key: KeyEvent.SpecialKey): Output = engine.key(KeyEvent.newBuilder().setSpecialKey(key).build())
        fun reading(output: Output): String = output.preedit.segmentList.joinToString("") { it.value }
        fun reset() { engine.command(SessionCommand.CommandType.RESET_CONTEXT) }

        try {
            engine.start()
            engine.command(SessionCommand.CommandType.SWITCH_COMPOSITION_MODE)

            reset()
            assertEquals("あ", reading(press('1')))
            repeat(4) { press('1') }
            assertEquals("ぁ", reading(press('1'))) // stock table: small kana after お

            reset()
            assertEquals("か", reading(press('2')))
            assertEquals("が", reading(press('*')))
            assertEquals("か", reading(press('*')))

            reset()
            assertEquals("は", reading(press('6')))
            assertEquals("ば", reading(press('*')))
            assertEquals("ぱ", reading(press('*')))
            assertEquals("は", reading(press('*')))

            reset()
            assertEquals("か", reading(press('2')))
            special(KeyEvent.SpecialKey.RIGHT)
            assertEquals("かか", reading(press('2'))) // cursor right ends toggling

            reset()
            repeat(3) { press('0') }
            assertEquals("ゎ", reading(press('0'))) // extra stock stage
            assertEquals("ー", reading(press('0')))

            reset()
            repeat(4) { press('#') }
            assertEquals("・", reading(press('#')))
            assertEquals("　", reading(press('#'))) // no ellipsis in stock table

            reset()
            press('1')
            assertEquals("あか", reading(press('2')))
            val converted = special(KeyEvent.SpecialKey.SPACE)
            assertTrue("Expected a conversion candidate for あか",
                converted.candidateWindow.candidateCount > 0 ||
                    converted.allCandidateWords.candidatesCount > 0 ||
                    converted.preedit.hasHighlightedPosition())
        } finally {
            engine.close()
        }
    }

    @Test fun nativeMidReadingInsertionBackspaceAndSessionRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = MozcEngine(context).also { it.setTwelveKeyInput(true) }
        fun reading(output: Output) = output.preedit.segmentList.joinToString("") { it.value }
        fun press(c: Char) = engine.key(KeyEvent.newBuilder().setKeyCode(c.code).build())
        fun special(key: KeyEvent.SpecialKey) = engine.key(KeyEvent.newBuilder().setSpecialKey(key).build())
        try {
            engine.start()
            engine.command(SessionCommand.CommandType.SWITCH_COMPOSITION_MODE)
            assertEquals("あ", reading(press('1')))
            // A pause longer than the conventional multi-tap timeout must not
            // commit the row or start a second character.
            Thread.sleep(1500)
            assertEquals("い", reading(press('1')))
            assertEquals("いか", reading(press('2')))
            special(KeyEvent.SpecialKey.LEFT)
            assertEquals("いさか", reading(press('3')))
            assertEquals("いか", reading(special(KeyEvent.SpecialKey.BACKSPACE)))
            assertEquals("い", reading(special(KeyEvent.SpecialKey.DEL)))
            assertEquals("", reading(special(KeyEvent.SpecialKey.BACKSPACE)))
            assertEquals("か", reading(press('2')))
            val committed = engine.command(SessionCommand.CommandType.SUBMIT)
            assertEquals("か", committed.result.value)
            assertEquals("", reading(committed))
            // Editor resets must reapply the twelve-key request.
            engine.start()
            engine.command(SessionCommand.CommandType.SWITCH_COMPOSITION_MODE)
            assertEquals("か", reading(press('2')))
        } finally {
            engine.close()
        }
    }
}
