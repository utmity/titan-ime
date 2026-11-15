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
 * Explicitly requires the separately installed Preview IME; never changes the
 * default input method or enables an IME on the user's behalf.
 */
class TwelveKeyPreviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var activity: InputTestActivity
    private var restoreEnglish = false
    private var registered = false
    private val ready = java.util.concurrent.atomic.AtomicInteger()
    private var japanese = false
    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            japanese = i?.getBooleanExtra("japanese", false) == true
            ready.incrementAndGet()
        }
    }

    @Before fun open() {
        assertEquals("Select Titan IME Preview before running this suite",
            "dev.utmity.titanime.preview/dev.utmity.titanime.TitanImeService",
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
            android.content.IntentFilter("dev.utmity.titanime.INPUT_READY"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        registered = true
        activity = instrumentation.startActivitySync(Intent(context, InputTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as InputTestActivity
        instrumentation.waitForIdleSync()
        val before = ready.get()
        instrumentation.runOnMainSync {
            activity.editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java).restartInput(activity.editor)
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (ready.get() == before && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Preview connected to editor", ready.get() > before)
        if (!japanese) { key(KeyEvent.KEYCODE_SPACE, meta = KeyEvent.META_FUNCTION_ON); restoreEnglish = true }
    }

    @After fun close() {
        if (::activity.isInitialized) {
            if (restoreEnglish) key(KeyEvent.KEYCODE_SPACE, meta = KeyEvent.META_FUNCTION_ON)
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
        key(KeyEvent.KEYCODE_A); key(KeyEvent.KEYCODE_E); expect("きか")
        key(KeyEvent.KEYCODE_Q); expect("きか", 1)
        key(KeyEvent.KEYCODE_R); expect("きさか", 2)
        key(KeyEvent.KEYCODE_DEL); expect("きか", 1)
        key(KeyEvent.KEYCODE_A); expect("きか")
        key(KeyEvent.KEYCODE_ENTER); expect("きか", composing = false)
        key(KeyEvent.KEYCODE_Q); expect("きか", 1, composing = false)
        key(KeyEvent.KEYCODE_A); expect("きか", composing = false)
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
        select(0, 2); key(KeyEvent.KEYCODE_Q); expect("あかさ", 0)
        select(0, 2); key(KeyEvent.KEYCODE_A); expect("あかさ", 2)
        key(KeyEvent.KEYCODE_F); expect("あかはさ", 3)
    }

    @Test fun editorCaretAndStaleBatchSnapshotKeepNativeReadingInSync() {
        instrumentation.runOnMainSync { activity.staleSnapshotDuringBatch = true }
        key(KeyEvent.KEYCODE_W); key(KeyEvent.KEYCODE_E); expect("あか")
        select(1, 1)
        key(KeyEvent.KEYCODE_R); expect("あさか", 2)
        key(KeyEvent.KEYCODE_DEL); expect("あか", 1)
        key(KeyEvent.KEYCODE_A); expect("あか")
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
}
