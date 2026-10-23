package dev.utmity.titanime

import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.assertEquals
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection

/** Injected Android events exercise the selected IME; they do not prove physical key mappings. */
class InputDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun testInputSequence() {
        val context = instrumentation.targetContext
        assertEquals("Select Titan IME manually before running this test",
            "dev.utmity.titanime/.TitanImeService",
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        val activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputTestActivity
        try {
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            fun key(code: Int, meta: Int = 0) {
                assertEquals("IME changed; abort input injection", "dev.utmity.titanime/.TitanImeService",
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
                instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
                instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
                instrumentation.waitForIdleSync()
                Thread.sleep(100)
                assertEquals("IME changed after key $code", "dev.utmity.titanime/.TitanImeService",
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            }
            fun expect(text: String, composing: Boolean) {
                instrumentation.runOnMainSync {
                    assertEquals(text, activity.editor.text.toString())
                    assertEquals("Composing span for '$text'", composing, BaseInputConnection.getComposingSpanStart(activity.editor.text) >= 0)
                }
            }
            key(KeyEvent.KEYCODE_A); expect("a", false)
            key(KeyEvent.KEYCODE_DEL); expect("", false)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            key(KeyEvent.KEYCODE_K); expect("k", true)
            key(KeyEvent.KEYCODE_DEL); expect("", false)
            key(KeyEvent.KEYCODE_K); key(KeyEvent.KEYCODE_A); expect("か", true)
            key(KeyEvent.KEYCODE_ENTER); expect("か\n", false)
            key(KeyEvent.KEYCODE_N); expect("か\nn", true)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON); expect("か\nん", false)
            key(KeyEvent.KEYCODE_B); expect("か\nんb", false)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun testComposer() {
        val composer = RomajiComposer()
        "konnichiha".forEach(composer::type)
        assertEquals("こんにちは", composer.text())
        composer.clear()
        "kitte".forEach(composer::type)
        assertEquals("きって", composer.text())
        composer.clear(); composer.type('n'); composer.flush()
        assertEquals("ん", composer.text())
        for ((input, expected) in mapOf("nn" to "ん", "nna" to "んな", "nnka" to "んか", "kanna" to "かんな")) {
            composer.clear()
            input.forEach(composer::type)
            composer.flush()
            assertEquals(input, expected, composer.text())
        }
    }
}
