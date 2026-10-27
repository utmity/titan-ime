package dev.utmity.titanime

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Opens and dismisses the real system picker; never selects/enables an IME. */
class ImePickerUiTest {
    @Test fun settingsOpensIndependentSystemPicker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun setting(key: String) = Settings.Secure.getString(context.contentResolver, key)
        val selected = setting(Settings.Secure.DEFAULT_INPUT_METHOD)
        val manager = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        fun enabledMethods() = manager.enabledInputMethodList.map { it.id }.sorted()
        val enabled = enabledMethods()
        val activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.waitForIdleSync()
        context.startActivity(Intent().setClassName("dev.utmity.titanime", "dev.utmity.titanime.SettingsActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
            listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
        var opened = false
        try {
            instrumentation.waitForIdleSync()
            var button: AccessibilityNodeInfo? = null
            val buttonDeadline = android.os.SystemClock.uptimeMillis() + 5000
            while (button == null && android.os.SystemClock.uptimeMillis() < buttonDeadline) {
                button = nodes(instrumentation.uiAutomation.rootInActiveWindow)
                    .firstOrNull { it.text?.toString() == "入力方法を切り替える" && it.isClickable }
                if (button == null) Thread.sleep(100)
            }
            assertNotNull("Settings picker button", button)
            assertTrue(button!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            opened = true
            var systemPicker = false
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (!systemPicker && android.os.SystemClock.uptimeMillis() < deadline) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                systemPicker = root?.packageName?.toString() in listOf("android", "com.android.systemui", "com.android.settings") &&
                    nodes(root).any { it.text?.toString()?.contains("Titan IME") == true }
                if (!systemPicker) Thread.sleep(100)
            }
            assertTrue("Android picker with enabled Titan entry", systemPicker)
        } finally {
            if (opened) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "dev.utmity.titanime") {
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            }
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
            assertEquals(selected, setting(Settings.Secure.DEFAULT_INPUT_METHOD))
            assertEquals(enabled, enabledMethods())
        }
    }
}
