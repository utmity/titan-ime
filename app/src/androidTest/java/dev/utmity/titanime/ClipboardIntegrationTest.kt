package dev.utmity.titanime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Runs in the IME process; temporary entries and settings are restored. */
class ClipboardIntegrationTest {
    @Test fun copyThenPhysicalPanelPaste() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val history = ClipboardHistory(context)
        val prefs = ImePreferences(context)
        val existingEntries = history.entries()
        val enabled = history.enabled
        val shortcut = prefs.clipboardKey
        org.junit.Assume.assumeTrue("Do not evict existing user history", history.entries().size < history.limit)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        var original: ClipData? = null
        var clipboardCaptured = false
        val token = "titan_history_test_${System.nanoTime()}"
        fun key(code: Int, meta: Int = 0) {
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
            instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
            Thread.sleep(150)
        }
        fun waitFor(condition: () -> Boolean) {
            val end = android.os.SystemClock.uptimeMillis() + 5000
            while (!condition() && android.os.SystemClock.uptimeMillis() < end) Thread.sleep(100)
            assertTrue(condition())
        }
        try {
            history.enabled = true; prefs.clipboardKey = "v"
            // Rebind after instrumentation replaces the target process. Never
            // enable a fallback IME or leave a different default selected.
            fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
            }
            val titan = "dev.utmity.titanime/.TitanImeService"
            val fallback = "com.iqqijni.bbkeyboard/.keyboard_service.view.HDKeyboardService"
            org.junit.Assume.assumeTrue(shell("settings get secure default_input_method").trim() == titan)
            if (shell("settings get secure enabled_input_methods").split(':').any { it.substringBefore(';').trim() == fallback }) {
                try { shell("ime set $fallback") } finally { shell("ime set $titan") }
            }
            instrumentation.uiAutomation.executeShellCommand("am start -W -n dev.utmity.titanime.harness/dev.utmity.titanime.InputTestActivity --activity-clear-task --activity-new-task").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
            Thread.sleep(1800)
            fun focus(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.className?.toString() == "android.widget.EditText") return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                return (0 until node.childCount).any { focus(node.getChild(it)) }
            }
            assertTrue("Focus test editor", focus(instrumentation.uiAutomation.rootInActiveWindow))
            Thread.sleep(800)
            instrumentation.runOnMainSync {
                original = clipboard.primaryClip
                clipboardCaptured = true
                clipboard.setPrimaryClip(ClipData.newPlainText("test", token))
            }
            waitFor { history.entries().any { it.text == token } }
            key(KeyEvent.KEYCODE_V, KeyEvent.META_FUNCTION_ON)
            // Select the temporary entry by its persisted list position, including pins.
            val index = history.entries().indexOfFirst { it.text == token }
            assertTrue(index >= 0)
            repeat(index) { key(KeyEvent.KEYCODE_J, KeyEvent.META_FUNCTION_ON) }
            key(KeyEvent.KEYCODE_ENTER)
            fun contains(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                return node.text?.toString() == token || (0 until node.childCount).any { contains(node.getChild(it)) }
            }
            waitFor { contains(instrumentation.uiAutomation.rootInActiveWindow) }
            history.pin(token); assertTrue(ClipboardHistory(context).entries().first { it.text == token }.pinned)
            history.delete(token); assertFalse(history.entries().any { it.text == token })
            assertTrue("Existing history and pins preserved", existingEntries == ClipboardHistory(context).entries())
            val secret = ClipData.newPlainText("test", "$token-secret")
            secret.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            instrumentation.runOnMainSync { clipboard.setPrimaryClip(secret) }
            Thread.sleep(300); assertFalse(history.entries().any { it.text == "$token-secret" })
        } finally {
            history.enabled = false
            history.delete(token); history.delete("$token-secret")
            if (clipboardCaptured) instrumentation.runOnMainSync { original?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip() }
            prefs.clipboardKey = shortcut; history.enabled = enabled
        }
    }
}
