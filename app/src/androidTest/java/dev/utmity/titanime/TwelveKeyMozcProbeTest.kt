package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJni
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*

/**
 * Native arm64 probe, deliberately separate from TitanImeService.
 * Not run by desktop :app:testDebugUnitTest; run on the actual device with
 * :app:connectedDebugAndroidTest or adb am instrument.
 *
 * Confirms the stock pinned Mozc 12-key table can be used through the SAME
 * JNI and dictionary packaged by Titan IME. It intentionally documents
 * stock behaviors that need customization to match the desired layout.
 */
class TwelveKeyMozcProbeTest {
    @Test fun nativeTwelveKeyTableToggleModifierCursorAndConversion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val initializer = MozcEngine(context)
        var session = 0L

        fun evaluate(input: Input.Builder): Output = synchronized(MozcEngine::class.java) {
            val bytes = Command.newBuilder().setInput(input).build().toByteArray()
            Command.parseFrom(MozcJni.evalCommand(bytes)).output
        }
        fun send(key: KeyEvent): Output = evaluate(
            Input.newBuilder().setType(Input.CommandType.SEND_KEY).setId(session).setKey(key)
        )
        fun press(character: Char): Output = send(KeyEvent.newBuilder().setKeyCode(character.code).build())
        fun special(key: KeyEvent.SpecialKey): Output = send(KeyEvent.newBuilder().setSpecialKey(key).build())
        fun command(type: SessionCommand.CommandType): Output = evaluate(
            Input.newBuilder().setType(Input.CommandType.SEND_COMMAND).setId(session)
                .setCommand(SessionCommand.newBuilder().setType(type))
        )
        fun reading(output: Output): String = output.preedit.segmentList.joinToString("") { it.value }
        fun reset() { command(SessionCommand.CommandType.RESET_CONTEXT) }

        try {
            // A non-default table overrides the romaji table in Config. No
            // production preference or normal key routing is changed.
            evaluate(Input.newBuilder().setType(Input.CommandType.SET_REQUEST).setRequest(
                Request.newBuilder()
                    .setSpecialRomanjiTable(Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA)
                    .setKanaModifierInsensitiveConversion(true)
            ))
            session = evaluate(Input.newBuilder().setType(Input.CommandType.CREATE_SESSION)).id
            assertTrue("Could not create Mozc native session", session != 0L)
            command(SessionCommand.CommandType.TURN_ON_IME)
            // Set mode explicitly so the test never depends on a default.
            evaluate(Input.newBuilder().setType(Input.CommandType.SEND_COMMAND).setId(session)
                .setCommand(SessionCommand.newBuilder()
                    .setType(SessionCommand.CommandType.SWITCH_COMPOSITION_MODE)
                    .setCompositionMode(CompositionMode.HIRAGANA)))

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
            if (session != 0L) evaluate(Input.newBuilder()
                .setType(Input.CommandType.DELETE_SESSION).setId(session))
            // Restore the normal request, including the usual suggestion config.
            initializer.configureSuggestions()
            initializer.close()
        }
    }
}
