package dev.utmity.titanime

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** End-to-end physical routing + Mozc + real EditText InputConnection.
 * Run against either package using -e imePackage. IME-switch/restart tests
 * require explicit runner arguments and always restore the selected IME.
 */
class TwelveKeyInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val imePackage = InstrumentationRegistry.getArguments().getString("imePackage") ?: "dev.utmity.titanime"
    private val component get() = "$imePackage/dev.utmity.titanime.TitanImeService"
    private lateinit var activity: InputTestActivity
    private var originalMode: String? = null
    private var inputMode = ""
    private var japanese = false
    private var swappedAxes = false
    private val leftKey get() = if (swappedAxes) KeyEvent.KEYCODE_O else KeyEvent.KEYCODE_Q
    private val rightKey get() = if (swappedAxes) KeyEvent.KEYCODE_L else KeyEvent.KEYCODE_A
    private val upKey get() = if (swappedAxes) KeyEvent.KEYCODE_Q else KeyEvent.KEYCODE_O
    private val downKey get() = if (swappedAxes) KeyEvent.KEYCODE_A else KeyEvent.KEYCODE_L
    private var registered = false
    private val ready = java.util.concurrent.atomic.AtomicInteger()
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            inputMode = i?.getStringExtra("input_mode") ?: ""
            japanese = i?.getBooleanExtra("japanese", false) == true
            swappedAxes = i?.getBooleanExtra("twelve_key_swap_cursor_axes", false) == true
            ready.incrementAndGet()
        }
    }

    @Before fun open() {
        assertTrue(imePackage in setOf("dev.utmity.titanime", "dev.utmity.titanime.preview"))
        val selected = android.content.ComponentName.unflattenFromString(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
        assertEquals("Select the requested Titan IME before running", android.content.ComponentName(imePackage,
            "dev.utmity.titanime.TitanImeService"), selected)
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter("dev.utmity.titanime.INPUT_READY"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        registered = true
        activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as InputTestActivity
        instrumentation.waitForIdleSync()
        reconnect()
        originalMode = inputMode
        setMode("KANA")
    }

    private fun reconnect() {
        val before = ready.get()
        instrumentation.runOnMainSync {
            activity.editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).restartInput(activity.editor)
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (ready.get() == before && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Titan connected to editor", ready.get() > before)
    }

    private fun setMode(target: String) {
        repeat(5) {
            if (inputMode == target) return
            key(KeyEvent.KEYCODE_SPACE, meta = KeyEvent.META_FUNCTION_ON)
            reconnect()
        }
        assertEquals("Default Fn+Space mode shortcut required", target, inputMode)
    }

    @After fun close() {
        if (::activity.isInitialized) {
            originalMode?.let { setMode(it) }
            instrumentation.runOnMainSync { activity.finish() }
        }
        if (registered) context.unregisterReceiver(receiver)
    }

    private fun key(code: Int, repeat: Int = 0, meta: Int = 0) {
        instrumentation.runOnMainSync { assertTrue(activity.hasWindowFocus() && activity.editor.hasFocus()) }
        val now = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, repeat, meta))
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        instrumentation.waitForIdleSync()
        Thread.sleep(100)
    }

    private fun expect(text: String, cursor: Int = text.length, composing: Boolean = true) {
        instrumentation.runOnMainSync {
            assertEquals(text, activity.editor.text.toString())
            assertEquals(cursor, activity.editor.selectionStart)
            assertEquals(cursor, activity.editor.selectionEnd)
            val start = BaseInputConnection.getComposingSpanStart(activity.editor.text)
            val end = BaseInputConnection.getComposingSpanEnd(activity.editor.text)
            if (composing) {
                assertEquals(0, start)
                assertEquals(text.length, end)
            } else {
                assertEquals(-1, start)
                assertEquals(-1, end)
            }
        }
    }

    private fun select(start: Int, end: Int) {
        instrumentation.runOnMainSync { activity.editor.setSelection(start, end) }
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
    }

    @Test fun pauseRepeatSeparatorInsertionDeletionAndCommit() {
        key(KeyEvent.KEYCODE_E); expect("か")
        Thread.sleep(1500)
        key(KeyEvent.KEYCODE_E); expect("き")
        key(KeyEvent.KEYCODE_E, repeat = 1); expect("き")
        key(rightKey); key(KeyEvent.KEYCODE_E); expect("きか")
        key(leftKey); expect("きか", 1)
        key(KeyEvent.KEYCODE_R); expect("きさか", 2)
        key(KeyEvent.KEYCODE_DEL); expect("きか", 1)
        key(rightKey); expect("きか")
        key(KeyEvent.KEYCODE_ENTER); expect("きか", composing = false)
        key(leftKey); expect("きか", 1, composing = false)
        key(rightKey); expect("きか", composing = false)
    }

    @Test fun selectedReadingIsReplacedInEngineAndEditor() {
        key(KeyEvent.KEYCODE_W); key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_R)
        expect("あかさ")
        select(1, 2)
        key(KeyEvent.KEYCODE_F); expect("あはさ", 2)
        key(KeyEvent.KEYCODE_T); expect("あばさ", 2)
        key(KeyEvent.KEYCODE_DEL); expect("あさ", 1)
        key(KeyEvent.KEYCODE_ENTER); expect("あさ", composing = false)
    }

    @Test fun mappedArrowsCollapseReadingSelectionAtItsEdges() {
        key(KeyEvent.KEYCODE_W); key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_R)
        select(0, 2); key(leftKey); expect("あかさ", 0)
        select(0, 2); key(rightKey); expect("あかさ", 2)
        key(KeyEvent.KEYCODE_F); expect("あかはさ", 3)
    }

    @Test fun editorCaretAndStaleBatchSnapshotKeepNativeReadingInSync() {
        instrumentation.runOnMainSync { activity.staleSnapshotDuringBatch = true }
        key(KeyEvent.KEYCODE_W); key(KeyEvent.KEYCODE_E); expect("あか")
        select(1, 1)
        key(KeyEvent.KEYCODE_R); expect("あさか", 2)
        key(KeyEvent.KEYCODE_DEL); expect("あか", 1)
        key(rightKey); expect("あか")
        key(KeyEvent.KEYCODE_SPACE)
        instrumentation.runOnMainSync {
            assertTrue("Space produced a nonempty conversion", activity.editor.text.isNotEmpty())
            assertEquals(0, BaseInputConnection.getComposingSpanStart(activity.editor.text))
        }
        key(KeyEvent.KEYCODE_ENTER)
        instrumentation.runOnMainSync {
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(activity.editor.text))
        }
    }
    @Test fun bothHalvesShareKanaEnglishAndNumericRows() {
        key(KeyEvent.KEYCODE_E); expect("か")
        key(KeyEvent.KEYCODE_U); expect("き")
        key(rightKey); key(KeyEvent.KEYCODE_U); expect("きか")
        setMode("ENGLISH")
        instrumentation.runOnMainSync { activity.editor.setText("") }
        reconnect()
        key(KeyEvent.KEYCODE_E); expect("a")
        key(KeyEvent.KEYCODE_U); expect("b")
        key(rightKey); key(KeyEvent.KEYCODE_E); expect("ba")
        setMode("NUMBER_HALF")
        instrumentation.runOnMainSync { activity.editor.setText("") }
        reconnect()
        key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_U); expect("22", composing = false)
        setMode("NUMBER_FULL")
        instrumentation.runOnMainSync { activity.editor.setText("") }
        reconnect()
        key(KeyEvent.KEYCODE_E); expect("２")
        key(KeyEvent.KEYCODE_U); expect("￥")
        key(rightKey); key(KeyEvent.KEYCODE_U)
        instrumentation.runOnMainSync { assertEquals("￥２", activity.editor.text.toString()) }
    }

    @Test fun committedTextUsesAllFourEditorDirectionsInEveryTwelveKeyMode() {
        for (mode in listOf("KANA", "ENGLISH", "NUMBER_HALF", "NUMBER_FULL")) {
            setMode(mode)
            instrumentation.runOnMainSync {
                activity.editor.setText("abc\ndef")
                activity.editor.setSelection(5)
            }
            reconnect()
            key(upKey); expect("abc\ndef", 1, composing = false)
            key(downKey); expect("abc\ndef", 5, composing = false)
            key(leftKey); expect("abc\ndef", 4, composing = false)
            key(rightKey); expect("abc\ndef", 5, composing = false)
        }
    }

    @Test fun mappedConversionDirectionsMatchRealDpad() {
        // Compare complete editing results against the native DPAD route,
        // rather than assuming a dictionary-dependent candidate spelling.
        fun convertAndNavigate(keys: List<Int>): Pair<String, Int> {
            instrumentation.runOnMainSync { activity.editor.setText("") }
            reconnect()
            key(KeyEvent.KEYCODE_W); key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_R)
            key(KeyEvent.KEYCODE_SPACE)
            keys.forEach { key(it) }
            var state = "" to 0
            instrumentation.runOnMainSync { state = activity.editor.text.toString() to activity.editor.selectionStart }
            return state
        }
        val mapped = convertAndNavigate(listOf(downKey, upKey, leftKey, rightKey))
        val physical = convertAndNavigate(listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(physical, mapped)
    }

    @Test fun qwertyLeavesLetterKeysUnmapped() {
        setMode("QWERTY")
        // Both Latin and Japanese QWERTY must consume these as letters, with
        // no synthetic DPAD reaching the editor, regardless of cursor setting.
        instrumentation.runOnMainSync { activity.receivedKeys.clear() }
        for (code in listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U)) key(code)
        instrumentation.runOnMainSync {
            assertTrue(activity.editor.text.isNotEmpty())
            assertTrue(activity.receivedKeys.none { it.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN) })
        }
    }

    @Test fun pCommitsEachTwelveKeyModeSkipsQwertyAndIgnoresRepeat() {
        for ((mode, text, next) in listOf(
            Triple("KANA", "か", "ENGLISH"), Triple("ENGLISH", "a", "NUMBER_HALF"),
            Triple("NUMBER_HALF", "2", "NUMBER_FULL"), Triple("NUMBER_FULL", "２", "KANA")
        )) {
            setMode(mode)
            instrumentation.runOnMainSync { activity.editor.setText("") }
            reconnect()
            key(KeyEvent.KEYCODE_E)
            expect(text, composing = mode != "NUMBER_HALF")
            key(KeyEvent.KEYCODE_P, repeat = 1)
            expect(text, composing = mode != "NUMBER_HALF")
            key(KeyEvent.KEYCODE_P)
            expect(text, composing = false)
            reconnect()
            assertEquals(next, inputMode)
        }
        setMode("QWERTY")
        instrumentation.runOnMainSync { activity.editor.setText("") }
        reconnect()
        key(KeyEvent.KEYCODE_P)
        instrumentation.runOnMainSync { assertEquals("p", activity.editor.text.toString()) }
        reconnect()
        assertEquals("QWERTY", inputMode)
    }

    @Test fun pPreservesConvertedCandidateBeforeChangingMode() {
        key(KeyEvent.KEYCODE_W); key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_SPACE)
        var converted = ""
        instrumentation.runOnMainSync { converted = activity.editor.text.toString() }
        assertTrue(converted.isNotEmpty())
        key(KeyEvent.KEYCODE_P)
        expect(converted, composing = false)
        reconnect()
        assertEquals("ENGLISH", inputMode)
    }

    @Test fun selectedModeSurvivesFieldAndInputConnectionChanges() {
        setMode("ENGLISH")
        val before = ready.get()
        instrumentation.runOnMainSync {
            activity.secondEditor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).restartInput(activity.secondEditor)
        }
        instrumentation.waitForIdleSync()
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (ready.get() == before && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Second field connected", ready.get() > before)
        assertEquals("ENGLISH", inputMode)
        reconnect()
        assertEquals("ENGLISH", inputMode)
        key(KeyEvent.KEYCODE_E); expect("a")
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForIme(expected: String) {
        val target = android.content.ComponentName.unflattenFromString(expected)
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val current = android.content.ComponentName.unflattenFromString(
                Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
            if (current == target) return
            Thread.sleep(50)
        }
        fail("IME selection did not reach $expected")
    }

    @Test fun modeRestoresAfterExplicitImeRoundTrip() {
        val other = InstrumentationRegistry.getArguments().getString("otherIme")
        org.junit.Assume.assumeTrue("Supply an enabled otherIme component to run OS-switch test", other != null)
        require(other!!.matches(Regex("[A-Za-z0-9_.$/]+")))
        val original = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        setMode("ENGLISH")
        key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_ENTER); expect("a", composing = false)
        try {
            shell("ime set $other"); waitForIme(other)
        } finally {
            shell("ime set $component"); waitForIme(original)
        }
        reconnect()
        assertEquals("ENGLISH", inputMode)
        expect("a", composing = false)
    }

    @Test fun modeRestoresAfterExplicitProcessRestart() {
        org.junit.Assume.assumeTrue("Use -e restartIme true for the process restart test",
            InstrumentationRegistry.getArguments().getString("restartIme") == "true")
        setMode("NUMBER_FULL")
        key(KeyEvent.KEYCODE_E); key(KeyEvent.KEYCODE_ENTER)
        try {
            shell("am force-stop $imePackage")
            context.startActivity(Intent().setClassName(imePackage, "dev.utmity.titanime.SettingsActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Thread.sleep(300)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        } finally {
            shell("ime set $component"); waitForIme(component)
        }
        reconnect()
        assertEquals("NUMBER_FULL", inputMode)
        instrumentation.runOnMainSync { assertEquals("２", activity.editor.text.toString()) }
    }

    @Test fun qwertyLanguageSwitchDoesNotChangeSavedInputMethod() {
        setMode("QWERTY")
        val before = japanese
        try {
            key(KeyEvent.KEYCODE_SPACE, meta = KeyEvent.META_FUNCTION_ON or KeyEvent.META_SHIFT_ON)
            reconnect()
            assertEquals("QWERTY", inputMode)
            assertEquals(!before, japanese)
            setMode("KANA")
            setMode("QWERTY")
            assertEquals(!before, japanese)
        } finally {
            setMode("QWERTY")
            if (japanese != before) {
                key(KeyEvent.KEYCODE_SPACE, meta = KeyEvent.META_FUNCTION_ON or KeyEvent.META_SHIFT_ON)
                reconnect()
            }
        }
    }

}
