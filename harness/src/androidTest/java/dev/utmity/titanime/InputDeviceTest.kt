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
        val connected = java.util.concurrent.CountDownLatch(1)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) { connected.countDown() }
        }
        val filter = android.content.IntentFilter("dev.utmity.titanime.INPUT_READY")
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        val activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputTestActivity
        try {
            instrumentation.waitForIdleSync()
            var ready = false
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    if (activity.editor.hasWindowFocus()) {
                        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                            .showSoftInput(activity.editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        ready = connected.count == 0L
                    }
                }
                Thread.sleep(100)
            }
            org.junit.Assert.assertTrue("Input connection not ready for the focused test editor", ready)
            Thread.sleep(1000)
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
            context.unregisterReceiver(receiver)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

}
