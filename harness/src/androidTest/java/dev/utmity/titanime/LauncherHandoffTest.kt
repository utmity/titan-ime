package dev.utmity.titanime

import android.content.Intent
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LauncherHandoffTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun key(code: Int, meta: Int = 0) {
        instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
        Thread.sleep(150)
    }
    private fun exercise(prefix: String, delay: Long, expected: String) {
        val context = instrumentation.targetContext
        var japanese = false
        val ready = java.util.concurrent.atomic.AtomicBoolean()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                japanese = i?.getBooleanExtra("japanese", false) == true; ready.set(true)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter("dev.utmity.titanime.INPUT_READY"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        val activity = instrumentation.startActivitySync(Intent(context, LauncherProxyActivity::class.java)
            .putExtra("prefix", prefix).putExtra("copy_delay", delay)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as LauncherProxyActivity
        var switchedMode = false
        try {
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { activity.proxy.requestFocus() }
                Thread.sleep(50)
            }
            assertTrue("Proxy input ready", ready.get())
            if (!japanese) { key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_FUNCTION_ON); switchedMode = true }
            Thread.sleep(250)
            // No IME window should have been requested just for this hidden editor.
            val automation = instrumentation.uiAutomation
            val flags = automation.serviceInfo.flags
            automation.serviceInfo = automation.serviceInfo.apply { this.flags = this.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            try { assertFalse(automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }) }
            finally { automation.serviceInfo = automation.serviceInfo.apply { this.flags = flags } }
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_H, 0))
            Thread.sleep(delay + 500)
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_H, 0))
            key(KeyEvent.KEYCODE_E)
            instrumentation.runOnMainSync {
                assertEquals(expected, activity.search.text.toString())
                if (prefix.isEmpty() && delay < 1500) {
                    assertEquals(0, android.view.inputmethod.BaseInputConnection.getComposingSpanStart(activity.search.text))
                    assertFalse("Consumed first Down must not leak Up into the new editor", activity.appKeys.any { it.keyCode == KeyEvent.KEYCODE_H && it.action == KeyEvent.ACTION_UP })
                }
            }
        } finally {
            if (switchedMode) key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_FUNCTION_ON)
            instrumentation.runOnMainSync { activity.finish() }
            context.unregisterReceiver(receiver)
        }
    }
    @Test fun hiddenProxyKeepsFirstLetterAndRelease() = exercise("", 100, "へ")
    @Test fun existingPrefixIsNeverReopened() = exercise("x", 100, "xhえ")
    @Test fun expiredReceiptIsNotReused() = exercise("", 1800, "hえ")
}
