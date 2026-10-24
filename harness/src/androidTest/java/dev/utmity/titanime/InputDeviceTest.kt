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
        val connected = java.util.concurrent.atomic.AtomicInteger()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) { connected.incrementAndGet() }
        }
        val filter = android.content.IntentFilter("dev.utmity.titanime.INPUT_READY")
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        val activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputTestActivity
        var targetEditor = activity.editor
        try {
            instrumentation.waitForIdleSync()
            var ready = false
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    if (activity.editor.hasWindowFocus()) {
                        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                            .showSoftInput(activity.editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                        ready = connected.get() > 0
                    }
                }
                Thread.sleep(100)
            }
            org.junit.Assert.assertTrue("Input connection not ready for the focused test editor", ready)
            Thread.sleep(1000)
            fun requireTestFocus() {
                instrumentation.runOnMainSync {
                    org.junit.Assert.assertTrue("Test editor lost focus; abort input injection",
                        activity.hasWindowFocus() && targetEditor.hasFocus())
                }
            }
            fun key(code: Int, meta: Int = 0) {
                requireTestFocus()
                assertEquals("IME changed; abort input injection", "dev.utmity.titanime/.TitanImeService",
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
                instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
                instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
                instrumentation.waitForIdleSync()
                Thread.sleep(100)
                requireTestFocus()
                assertEquals("IME changed after key $code", "dev.utmity.titanime/.TitanImeService",
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            }
            fun expect(text: String, composing: Boolean) {
                instrumentation.runOnMainSync {
                    assertEquals(text, targetEditor.text.toString())
                    assertEquals("Composing span for '$text'", composing, BaseInputConnection.getComposingSpanStart(targetEditor.text) >= 0)
                }
            }
            for ((code, symbol) in listOf(KeyEvent.KEYCODE_MINUS to "-", KeyEvent.KEYCODE_COMMA to ",",
                KeyEvent.KEYCODE_PERIOD to ".", KeyEvent.KEYCODE_LEFT_BRACKET to "[", KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
                KeyEvent.KEYCODE_APOSTROPHE to "'")) {
                key(code); expect(symbol, false)
                key(KeyEvent.KEYCODE_DEL); expect("", false)
            }
            key(KeyEvent.KEYCODE_A); expect("a", false)
            key(KeyEvent.KEYCODE_DEL); expect("", false)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            key(KeyEvent.KEYCODE_K); expect("k", true)
            key(KeyEvent.KEYCODE_DEL); expect("", false)
            key(KeyEvent.KEYCODE_K); key(KeyEvent.KEYCODE_A); expect("か", true)
            key(KeyEvent.KEYCODE_ENTER); expect("か", false)
            key(KeyEvent.KEYCODE_ENTER); expect("か\n", false)
            key(KeyEvent.KEYCODE_N); expect("か\nn", true)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON); expect("か\nん", false)
            key(KeyEvent.KEYCODE_B); expect("か\nんb", false)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            // Synthetic regression using the modifier observed on physical Sym+A.
            key(KeyEvent.KEYCODE_A, KeyEvent.META_SYM_ON); expect("か\nんba", false)
            // Reproduce Titan's vendor Fn down/A/up sequence; this is not a physical press.
            fun vendorFn(action: Int) {
                requireTestFocus()
                instrumentation.sendKeySync(KeyEvent(0, 0, action, 403, 0, 0, 0, 251))
                instrumentation.waitForIdleSync()
                Thread.sleep(100)
            }
            vendorFn(KeyEvent.ACTION_DOWN)
            try {
                key(KeyEvent.KEYCODE_A); expect("か\nんbaa", false)
            } finally {
                vendorFn(KeyEvent.ACTION_UP)
            }
            key(KeyEvent.KEYCODE_K); key(KeyEvent.KEYCODE_A); expect("か\nんbaaか", true)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            expect("か\nんbaaか", false)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            key(KeyEvent.KEYCODE_N)
            key(KeyEvent.KEYCODE_A, KeyEvent.META_SYM_ON); expect("か\nんbaaかんa", false)
            key(KeyEvent.KEYCODE_K)
            vendorFn(KeyEvent.ACTION_DOWN)
            try {
                key(KeyEvent.KEYCODE_A); expect("か\nんbaaかんaka", false)
            } finally {
                vendorFn(KeyEvent.ACTION_UP)
            }
            key(KeyEvent.KEYCODE_A, KeyEvent.META_FUNCTION_ON); expect("か\nんbaaかんakaa", false)
            key(KeyEvent.KEYCODE_K); key(KeyEvent.KEYCODE_A); expect("か\nんbaaかんakaaか", true)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            expect("か\nんbaaかんakaaか", false)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            var expected = "か\nんbaaかんakaaか"
            for ((code, symbol) in listOf(KeyEvent.KEYCODE_PERIOD to "。", KeyEvent.KEYCODE_COMMA to "、", KeyEvent.KEYCODE_MINUS to "ー", KeyEvent.KEYCODE_LEFT_BRACKET to "「", KeyEvent.KEYCODE_RIGHT_BRACKET to "」", KeyEvent.KEYCODE_1 to "1")) {
                key(KeyEvent.KEYCODE_N)
                key(code)
                expected += "ん$symbol"
                expect(expected, code != KeyEvent.KEYCODE_1)
                key(KeyEvent.KEYCODE_K); key(KeyEvent.KEYCODE_A)
                expected += "か"
                expect(expected, true)
                key(KeyEvent.KEYCODE_ENTER)
                expect(expected, false)
                key(KeyEvent.KEYCODE_ENTER)
                expected += "\n"
                expect(expected, false)
            }
            // Mozc conversion: physical-key equivalent injection into the real InputConnection.
            fun clearEditor() {
                val before = connected.get()
                instrumentation.runOnMainSync {
                    activity.editor.setText("")
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .restartInput(activity.editor)
                }
                val timeout = android.os.SystemClock.uptimeMillis() + 10000
                while (connected.get() == before && android.os.SystemClock.uptimeMillis() < timeout) Thread.sleep(50)
                org.junit.Assert.assertTrue("Input must reconnect after clearing editor", connected.get() > before)
                instrumentation.waitForIdleSync()
            }
            fun type(text: String) { text.forEach { key(KeyEvent.KEYCODE_A + (it - 'a')) } }
            clearEditor()
            // A modifier press alone must not commit before its punctuation arrives.
            type("ka")
            vendorFn(KeyEvent.ACTION_DOWN)
            try {
                expect("か", true)
                key(KeyEvent.KEYCODE_MINUS); expect("かー", true)
            } finally { vendorFn(KeyEvent.ACTION_UP) }
            key(KeyEvent.KEYCODE_ENTER); expect("かー", false)
            clearEditor()
            type("ka")
            key(KeyEvent.KEYCODE_SYM, KeyEvent.META_SYM_ON); expect("か", true)
            key(KeyEvent.KEYCODE_COMMA, KeyEvent.META_SYM_ON); expect("か、", true)
            key(KeyEvent.KEYCODE_PERIOD, KeyEvent.META_FUNCTION_ON); expect("か、。", true)
            key(KeyEvent.KEYCODE_ENTER); expect("か、。", false)
            clearEditor()
            type("nihongo"); expect("にほんご", true)
            key(KeyEvent.KEYCODE_SPACE); expect("日本語", true)
            key(KeyEvent.KEYCODE_SPACE)
            var alternate = ""
            instrumentation.runOnMainSync { alternate = activity.editor.text.toString() }
            org.junit.Assert.assertNotEquals("Space must select another candidate", "日本語", alternate)
            org.junit.Assert.assertTrue("Candidate must not be empty", alternate.isNotEmpty())
            expect(alternate, true)
            key(KeyEvent.KEYCODE_ENTER); expect(alternate, false)
            key(KeyEvent.KEYCODE_ENTER); expect("$alternate\n", false)
            clearEditor()
            type("nihongo"); key(KeyEvent.KEYCODE_SPACE); key(KeyEvent.KEYCODE_SPACE)
            fun tapImeText(label: String) {
                val automation = instrumentation.uiAutomation
                val oldFlags = automation.serviceInfo.flags
                automation.serviceInfo = automation.serviceInfo.apply {
                    flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
                try {
                    var candidate: android.view.accessibility.AccessibilityNodeInfo? = null
                    val timeout = android.os.SystemClock.uptimeMillis() + 5000
                    while (candidate == null && android.os.SystemClock.uptimeMillis() < timeout) {
                        for (window in automation.windows) {
                            val root = window.root ?: continue
                            if (root.packageName?.toString() != "dev.utmity.titanime") continue
                            candidate = root.findAccessibilityNodeInfosByText(label).firstOrNull {
                                it.text?.toString() == label && it.isClickable && it.isVisibleToUser
                            }
                            if (candidate != null) break
                        }
                        if (candidate == null) Thread.sleep(100)
                    }
                    org.junit.Assert.assertNotNull("Visible, clickable Japanese candidate required", candidate)
                    val bounds = android.graphics.Rect()
                    candidate!!.getBoundsInScreen(bounds)
                    val screen = context.resources.displayMetrics
                    org.junit.Assert.assertTrue("Candidate must be within screen", !bounds.isEmpty &&
                        bounds.left >= 0 && bounds.top >= 0 && bounds.right <= screen.widthPixels &&
                        bounds.bottom <= screen.heightPixels)
                    requireTestFocus()
                    val now = android.os.SystemClock.uptimeMillis()
                    for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                        val touch = android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(),
                            action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                        touch.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        org.junit.Assert.assertTrue("Candidate touch injection", automation.injectInputEvent(touch, true))
                        touch.recycle()
                    }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(100)
                } finally {
                    automation.serviceInfo = automation.serviceInfo.apply { flags = oldFlags }
                }
            }
            tapImeText("日本語")
            expect("日本語", true)
            key(KeyEvent.KEYCODE_ENTER); expect("日本語", false)
            clearEditor()
            type("nihongo"); key(KeyEvent.KEYCODE_SPACE); expect("日本語", true)
            key(KeyEvent.KEYCODE_ESCAPE); expect("にほんご", true)
            key(KeyEvent.KEYCODE_ENTER); expect("にほんご", false)
            // Former custom-composer regressions now exercise Mozc itself on Android.
            for ((roman, kana) in mapOf("konnichiha" to "こんにちは", "kitte" to "きって", "nna" to "んな", "kanna" to "かんな")) {
                clearEditor(); type(roman); expect(kana, true)
                key(KeyEvent.KEYCODE_ENTER); expect(kana, false)
            }
            clearEditor()
            type("nihongo"); expect("にほんご", true)
            instrumentation.runOnMainSync { activity.editor.setSelection(0) }
            instrumentation.waitForIdleSync()
            Thread.sleep(150)
            type("ka"); expect("かにほんご", true)
            key(KeyEvent.KEYCODE_ENTER); expect("かにほんご", false)

            clearEditor()
            // External ranges must replace only the selection, in reading and conversion.
            for (convert in listOf(false, true)) {
                type("nihongo")
                if (convert) key(KeyEvent.KEYCODE_SPACE)
                instrumentation.runOnMainSync { activity.editor.setSelection(0, 2) }
                instrumentation.waitForIdleSync()
                Thread.sleep(150)
                type("ka")
                val replacement = if (convert) "か語" else "かんご"
                expect(replacement, true)
                key(KeyEvent.KEYCODE_ENTER); expect(replacement, false)
                clearEditor()
            }
            // Before conversion, arrows commit the reading and move the editor caret.
            type("kana")
            key(KeyEvent.KEYCODE_DPAD_LEFT); expect("かな", false)
            type("ki"); expect("かきな", true)
            key(KeyEvent.KEYCODE_ENTER); expect("かきな", false)
            clearEditor()
            type("n")
            key(KeyEvent.KEYCODE_DPAD_LEFT); expect("ん", false)
            type("ka"); expect("かん", true)
            key(KeyEvent.KEYCODE_ENTER); expect("かん", false)
            clearEditor()
            type("nihongowobenkyousuru")
            key(KeyEvent.KEYCODE_SPACE)
            fun highlightedRange(): Pair<Int, Int> {
                var range = -1 to -1
                instrumentation.runOnMainSync {
                    val text = targetEditor.text
                    val spans = text.getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java)
                    org.junit.Assert.assertEquals("Exactly one focused Mozc segment", 1, spans.size)
                    range = text.getSpanStart(spans[0]) to text.getSpanEnd(spans[0])
                }
                return range
            }
            val firstSegment = highlightedRange()
            vendorFn(KeyEvent.ACTION_DOWN)
            try {
                key(KeyEvent.KEYCODE_L)
                org.junit.Assert.assertTrue(highlightedRange().first > firstSegment.first)
                key(KeyEvent.KEYCODE_H)
                assertEquals(firstSegment, highlightedRange())
                key(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON)
                key(KeyEvent.KEYCODE_L, KeyEvent.META_SHIFT_ON)
                org.junit.Assert.assertNotEquals(firstSegment, highlightedRange())
                key(KeyEvent.KEYCODE_H, KeyEvent.META_SHIFT_ON)
                assertEquals(firstSegment, highlightedRange())
            } finally { vendorFn(KeyEvent.ACTION_UP) }

            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            val secondSegment = highlightedRange()
            org.junit.Assert.assertTrue("Right must focus a later segment", secondSegment.first > firstSegment.first)
            key(KeyEvent.KEYCODE_DPAD_LEFT)
            assertEquals(firstSegment, highlightedRange())
            tapImeText("次文節")
            assertEquals(secondSegment, highlightedRange())
            tapImeText("前文節")
            assertEquals(firstSegment, highlightedRange())
            key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
            org.junit.Assert.assertNotEquals("Shift+Right must resize the segment", firstSegment, highlightedRange())
            var converted = ""
            instrumentation.runOnMainSync { converted = targetEditor.text.toString() }
            key(KeyEvent.KEYCODE_ENTER); expect(converted, false)

            clearEditor()
            type("ka"); expect("か", true)
            fun focus(editor: android.widget.EditText) {
                val before = connected.get()
                targetEditor = editor
                instrumentation.runOnMainSync { editor.requestFocus() }
                val timeout = android.os.SystemClock.uptimeMillis() + 10000
                while (connected.get() == before && android.os.SystemClock.uptimeMillis() < timeout) Thread.sleep(50)
                org.junit.Assert.assertTrue("New field must connect", connected.get() > before)
                instrumentation.waitForIdleSync()
            }
            focus(activity.secondEditor)
            type("ki"); expect("き", true)
            key(KeyEvent.KEYCODE_ENTER); expect("き", false)
            focus(activity.editor)
            expect("か", false)
            type("ku"); expect("かく", true)
            key(KeyEvent.KEYCODE_ENTER); expect("かく", false)
            key(KeyEvent.KEYCODE_J, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        } finally {
            context.unregisterReceiver(receiver)
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

}
