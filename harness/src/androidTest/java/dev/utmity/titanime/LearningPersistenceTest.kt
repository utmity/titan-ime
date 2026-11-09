package dev.utmity.titanime

import android.content.Intent
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Run separately after the full regression and a real IME process restart. */
class LearningPersistenceTest {
    @Test fun learnedCandidateAfterRestart() {
        val test = InstrumentationRegistry.getInstrumentation()
        val context = test.targetContext
        val expected = context.getSharedPreferences("test_expectations", 0).getString("suisei", null)
        assertNotNull("Run InputDeviceTest first", expected)
        val connected = java.util.concurrent.atomic.AtomicBoolean()
        val japanese = java.util.concurrent.atomic.AtomicBoolean()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                japanese.set(intent?.getBooleanExtra("japanese", false) == true)
                connected.set(true)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter("dev.utmity.titanime.INPUT_READY"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        val activity = test.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputTestActivity
        try {
            test.waitForIdleSync()
            test.runOnMainSync {
                activity.editor.requestFocus()
                activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).restartInput(activity.editor)
            }
            val deadline = android.os.SystemClock.uptimeMillis() + 15000
            while (!connected.get() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
            assertTrue("Input connection ready", connected.get())
            fun key(code: Int, scan: Int = 0, action: Int? = null) {
                test.runOnMainSync { assertTrue(activity.hasWindowFocus() && activity.editor.hasFocus()) }
                assertEquals("dev.utmity.titanime/.TitanImeService", android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD))
                for (a in if (action == null) listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP) else listOf(action))
                    test.sendKeySync(KeyEvent(0, 0, a, code, 0, 0, 0, scan))
                test.waitForIdleSync(); Thread.sleep(100)
            }
            fun toggle() { key(403, 251, KeyEvent.ACTION_DOWN); key(KeyEvent.KEYCODE_SPACE); key(403, 251, KeyEvent.ACTION_UP) }
            if (!japanese.get()) toggle()
            "suisei".forEach { key(KeyEvent.KEYCODE_A + (it - 'a')) }
            key(KeyEvent.KEYCODE_SPACE)
            test.runOnMainSync { assertEquals("Learned candidate survives IME process restart", expected, activity.editor.text.toString()) }
            key(KeyEvent.KEYCODE_ENTER); toggle()
        } finally {
            context.unregisterReceiver(receiver)
            test.runOnMainSync { activity.finish() }
        }
    }
}
