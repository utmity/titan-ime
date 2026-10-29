package dev.utmity.titanime

import android.content.Intent
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

/** Hostile editor simulations on a real InputConnection, not Discord verification. */
class EditorCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var activity: InputTestActivity
    private var restoreEnglish = false
    private val ready = java.util.concurrent.atomic.AtomicInteger()
    private var initialJapanese = false
    private var web: android.webkit.WebView? = null
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            initialJapanese = i?.getBooleanExtra("japanese", false) == true
            ready.incrementAndGet()
        }
    }
    @Before fun open() {
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter("dev.utmity.titanime.INPUT_READY"), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as InputTestActivity
        instrumentation.waitForIdleSync()
        reconnect()
        if (!initialJapanese) { key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_FUNCTION_ON); restoreEnglish = true }
    }
    @After fun close() {
        if (::activity.isInitialized) {
            if (restoreEnglish) key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_FUNCTION_ON)
            instrumentation.runOnMainSync { web?.destroy(); activity.finish() }
        }
        context.unregisterReceiver(receiver)
    }
    private fun reconnect() {
        val before = ready.get()
        var restarted = false
        val end = android.os.SystemClock.uptimeMillis() + 5000
        while (ready.get() == before && android.os.SystemClock.uptimeMillis() < end) {
            instrumentation.runOnMainSync {
                activity.editor.requestFocus()
                if (activity.editor.hasWindowFocus()) {
                    val manager = activity.getSystemService(InputMethodManager::class.java)
                    if (!restarted) { manager.restartInput(activity.editor); restarted = true }
                    manager.showSoftInput(activity.editor, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            Thread.sleep(50)
        }
        assertTrue("Editor connection ready", ready.get() > before)
    }
    private fun key(code: Int, meta: Int = 0) {
        instrumentation.runOnMainSync { assertTrue(activity.hasWindowFocus() && (web?.hasFocus() ?: activity.editor.hasFocus())) }
        instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        instrumentation.sendKeySync(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
        instrumentation.waitForIdleSync(); Thread.sleep(100)
    }
    private fun type(value: String) { value.forEach { key(KeyEvent.KEYCODE_A + (it - 'a')) } }
    private fun expect(value: String) = instrumentation.runOnMainSync { assertEquals(value, activity.editor.text.toString()) }

    private fun candidatesVisible() {
        val automation = instrumentation.uiAutomation
        val flagsBefore = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun containsCandidate(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isVisibleToUser && node.contentDescription?.toString()?.endsWith(" 候補") == true) return true
            return (0 until node.childCount).any { containsCandidate(node.getChild(it)) }
        }
        try {
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (android.os.SystemClock.uptimeMillis() < deadline) {
                if (automation.windows.any { containsCandidate(it.root) }) return
                Thread.sleep(100)
            }
            val capture = automation.takeScreenshot()
            val path = java.io.File(context.getExternalFilesDir(null), "candidate-failure-${System.currentTimeMillis()}.png")
            path.outputStream().use { capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            capture.recycle()
            fail("Native candidates must be visible in the IME window: ${path.name}")
        } finally { automation.serviceInfo = automation.serviceInfo.apply { flags = flagsBefore } }
    }

    @Test fun candidatesAfterHideAndRefocus() {
        type("nihongo"); key(KeyEvent.KEYCODE_SPACE); candidatesVisible()
        var converted = ""
        instrumentation.runOnMainSync { converted = activity.editor.text.toString() }
        instrumentation.runOnMainSync {
            activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(activity.editor.windowToken, 0)
        }
        // Android finishes composing text when hiding the input view. A plain
        // Space after that is not a conversion request; test fresh input instead.
        Thread.sleep(300); expect(converted)
        type("ka"); key(KeyEvent.KEYCODE_SPACE); candidatesVisible()
        instrumentation.runOnMainSync { activity.secondEditor.requestFocus() }
        Thread.sleep(200)
        instrumentation.runOnMainSync { activity.editor.setText("") }
        reconnect(); type("ka"); key(KeyEvent.KEYCODE_SPACE); candidatesVisible()
    }

    @Test fun localWebFormConversionAndControlledRewrite() {
        val loaded = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            web = android.webkit.WebView(activity).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) { loaded.countDown() }
                }
            }
            activity.setContentView(web)
            web!!.loadDataWithBaseURL("https://titan.invalid/", "<html><body><textarea id='e' style='width:90%;height:160px;font-size:24px'></textarea></body></html>", "text/html", "UTF-8", null)
        }
        assertTrue(loaded.await(5, java.util.concurrent.TimeUnit.SECONDS))
        fun js(script: String): String {
            val latch = java.util.concurrent.CountDownLatch(1)
            var value = ""
            instrumentation.runOnMainSync { web!!.evaluateJavascript(script) { value = it; latch.countDown() } }
            assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
            return value
        }
        instrumentation.runOnMainSync { web!!.requestFocus() }
        js("document.getElementById('e').focus()")
        Thread.sleep(500)
        type("nihongo")
        assertEquals("Web preedit before conversion", "\"にほんご\"", js("document.getElementById('e').value"))
        key(KeyEvent.KEYCODE_SPACE)
        val converted = js("document.getElementById('e').value")
        assertNotEquals("Web conversion is nonempty", "\"\"", converted)
        candidatesVisible(); key(KeyEvent.KEYCODE_ENTER)
        assertEquals(converted, js("document.getElementById('e').value"))
        js("var e=document.getElementById('e');e.value='prefix';e.setSelectionRange(6,6)")
        Thread.sleep(200); type("ka"); key(KeyEvent.KEYCODE_ENTER)
        assertEquals("\"prefixか\"", js("document.getElementById('e').value"))
    }

    @Test fun restartPreservesVisibleReading() {
        type("nihongo"); expect("にほんご")
        reconnect()
        type("ka"); expect("にほんごか")
        key(KeyEvent.KEYCODE_SPACE); key(KeyEvent.KEYCODE_ENTER)
    }
    @Test fun batchSnapshotLagDoesNotFinalizeCompositionOrSpace() {
        instrumentation.runOnMainSync { activity.staleSnapshotDuringBatch = true }
        type("nihongo"); expect("にほんご")
        fun composing() = instrumentation.runOnMainSync {
            assertEquals("Whole reading remains composing", 0, BaseInputConnection.getComposingSpanStart(activity.editor.text))
            assertEquals(activity.editor.text.length, BaseInputConnection.getComposingSpanEnd(activity.editor.text))
        }
        composing()
        key(KeyEvent.KEYCODE_SPACE); composing()
        var firstCandidate = ""
        instrumentation.runOnMainSync { firstCandidate = activity.editor.text.toString() }
        key(KeyEvent.KEYCODE_SPACE); composing()
        instrumentation.runOnMainSync { assertNotEquals("Second Space cycles", firstCandidate, activity.editor.text.toString()) }
        key(KeyEvent.KEYCODE_ENTER)
        instrumentation.runOnMainSync { assertEquals(-1, BaseInputConnection.getComposingSpanStart(activity.editor.text)) }
    }
    @Test fun nonTextVisiblePasswordFlagsRemainDirect() {
        // Observed Discord field metadata: no text class, visible-password bits.
        // Never silently turn a protected/non-text field into a learning editor.
        instrumentation.runOnMainSync { activity.editor.setRawInputType(0x90) }
        reconnect(); type("ka"); expect("ka")
        instrumentation.runOnMainSync {
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(activity.editor.text))
            activity.editor.setText("")
            activity.editor.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        reconnect(); type("ka"); expect("か")
        key(KeyEvent.KEYCODE_ENTER)
    }
    @Test fun sameLengthRewriteDoesNotResurrectOldReading() = rewrite("別")
    @Test fun differentLengthRewriteDoesNotResurrectOldReading() = rewrite("別名")
    private fun rewrite(replacement: String) {
        type("k")
        instrumentation.runOnMainSync {
            activity.editor.setText(replacement)
            BaseInputConnection.setComposingSpans(activity.editor.text)
            activity.editor.setSelection(replacement.length)
        }
        instrumentation.waitForIdleSync(); Thread.sleep(150)
        type("a"); expect(replacement + "あ")
        key(KeyEvent.KEYCODE_ENTER)
    }
    @Test fun synchronousSameLengthNormalization() = normalize("別")
    @Test fun openingAndClosingSupportUiPreservesComposition() {
        type("ka"); expect("か")
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun click(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.contentDescription?.toString() == "…") return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            return (0 until node.childCount).any { click(node.getChild(it)) }
        }
        try {
            assertTrue(automation.windows.any { click(it.root) })
            Thread.sleep(150)
            instrumentation.runOnMainSync { assertEquals(0, BaseInputConnection.getComposingSpanStart(activity.editor.text)) }
            key(KeyEvent.KEYCODE_ESCAPE); expect("か")
            key(KeyEvent.KEYCODE_V, KeyEvent.META_FUNCTION_ON)
            key(KeyEvent.KEYCODE_ESCAPE); expect("か")
            instrumentation.runOnMainSync { assertEquals(0, BaseInputConnection.getComposingSpanStart(activity.editor.text)) }
            key(KeyEvent.KEYCODE_SPACE); key(KeyEvent.KEYCODE_ENTER)
        } finally { automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags } }
    }
    @Test fun physicalNavigationPreservesReadingAndSelection() {
        type("nihongo")
        key(KeyEvent.KEYCODE_H, KeyEvent.META_FUNCTION_ON)
        type("ka"); expect("にほんかご")
        key(KeyEvent.KEYCODE_DEL); expect("にほんご")
        key(KeyEvent.KEYCODE_SPACE); key(KeyEvent.KEYCODE_ENTER)
        instrumentation.runOnMainSync { activity.editor.setText("ab😀c\nxyz"); activity.editor.setSelection(5) }
        reconnect()
        key(KeyEvent.KEYCODE_H, KeyEvent.META_FUNCTION_ON)
        instrumentation.runOnMainSync { assertEquals(4, activity.editor.selectionStart) }
        key(KeyEvent.KEYCODE_H, KeyEvent.META_FUNCTION_ON)
        instrumentation.runOnMainSync { assertEquals("Must not split surrogate pair", 2, activity.editor.selectionStart) }
        key(KeyEvent.KEYCODE_L, KeyEvent.META_FUNCTION_ON or KeyEvent.META_SHIFT_ON)
        instrumentation.runOnMainSync { assertEquals(2, activity.editor.selectionStart); assertEquals(4, activity.editor.selectionEnd) }
        instrumentation.runOnMainSync { activity.editor.setText("Ae\u0301B\nxyz"); activity.editor.setSelection(1) }
        reconnect()
        key(KeyEvent.KEYCODE_L, KeyEvent.META_FUNCTION_ON or KeyEvent.META_SHIFT_ON)
        instrumentation.runOnMainSync { assertEquals(1, activity.editor.selectionStart); assertEquals(3, activity.editor.selectionEnd) }
        key(KeyEvent.KEYCODE_L, KeyEvent.META_FUNCTION_ON or KeyEvent.META_SHIFT_ON)
        instrumentation.runOnMainSync { assertEquals(1, activity.editor.selectionStart); assertEquals(4, activity.editor.selectionEnd) }
        instrumentation.runOnMainSync { activity.editor.setSelection(1) }
        key(KeyEvent.KEYCODE_J, KeyEvent.META_FUNCTION_ON)
        instrumentation.runOnMainSync { assertTrue("Move to second line", activity.editor.selectionStart >= 5) }
        key(KeyEvent.KEYCODE_K, KeyEvent.META_FUNCTION_ON)
        instrumentation.runOnMainSync { assertTrue("Move back to first line", activity.editor.selectionStart < 5) }
    }
    @Test fun synchronousDifferentLengthNormalization() = normalize("別名")
    private fun normalize(replacement: String) {
        instrumentation.runOnMainSync { activity.rewriteNextComposition = replacement }
        type("k"); expect(replacement)
        type("a"); expect(replacement + "あ")
        key(KeyEvent.KEYCODE_ENTER)
    }
    @Test fun conversionDoesNotOverrideEditorColors() {
        for (dark in listOf(false, true)) {
            instrumentation.runOnMainSync {
                activity.editor.setText("")
                activity.editor.setTextColor(if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                activity.editor.setBackgroundColor(if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            reconnect(); type("nihongo"); key(KeyEvent.KEYCODE_SPACE)
            instrumentation.runOnMainSync {
                val text = activity.editor.text
                assertTrue("Conversion composition remains", BaseInputConnection.getComposingSpanStart(text) >= 0)
                assertEquals(0, text.getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java).size)
                assertEquals(0, text.getSpans(0, text.length, android.text.style.ForegroundColorSpan::class.java).size)
                assertEquals(1, text.getSpans(0, text.length, android.text.style.StyleSpan::class.java).size)
            }
            val capture = instrumentation.uiAutomation.takeScreenshot()
            java.io.File(context.getExternalFilesDir(null), if (dark) "editor-dark.png" else "editor-light.png")
                .outputStream().use { capture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            capture.recycle()
            key(KeyEvent.KEYCODE_ENTER)
        }
    }
}
