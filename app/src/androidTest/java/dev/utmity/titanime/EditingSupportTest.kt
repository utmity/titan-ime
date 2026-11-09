package dev.utmity.titanime

import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class EditingSupportTest {
    @Test fun absentAutofillProviderIsSafe() {
        if (android.os.Build.VERSION.SDK_INT < 30) return
        val service = TitanImeService()
        assertNull(service.onCreateInlineSuggestionsRequest(android.os.Bundle.EMPTY))
    }
    @Test fun boundedPrivateHistory() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("test_history_${name}", mode)
        }
        val history = ClipboardHistory(isolated)
        try {
            history.clear(); history.enabled = false
            history.capture("not-saved", false); assertTrue(history.entries().isEmpty())
            history.enabled = true; history.limit = 5
            history.capture("secret", true); assertTrue(history.entries().isEmpty())
            history.capture("x".repeat(10001), false); assertTrue(history.entries().isEmpty())
            history.capture("pinned", false); history.pin("pinned")
            repeat(8) { history.capture("entry$it", false) }
            assertEquals(5, history.entries().size); assertEquals("pinned", history.entries().first().text)
            history.capture("entry7", false); assertEquals(5, history.entries().size)
            assertEquals(history.entries(), ClipboardHistory(isolated).entries())
            history.delete("pinned"); assertFalse(history.entries().any { it.pinned })
            history.enabled = false; val old = history.entries(); history.capture("ignored", false); assertEquals(old, history.entries())
            history.clear(); assertTrue(history.entries().isEmpty())
        } finally { isolated.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE).edit().clear().commit() }
    }
    @Test fun navigationDoesNotStealMetaOrAlt() {
        fun event(meta: Int) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_H, 0, meta)
        assertNull(EditingBindings.direction(event(0), false, true))
        assertNull(EditingBindings.direction(event(KeyEvent.META_ALT_ON), true, true))
        assertNull(EditingBindings.direction(event(KeyEvent.META_META_ON), true, true))
        assertNull(EditingBindings.direction(event(0), true, false))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, EditingBindings.direction(event(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON), true, true))
        val received = mutableListOf<KeyEvent>()
        val connection = object : android.view.inputmethod.BaseInputConnection(android.view.View(InstrumentationRegistry.getInstrumentation().targetContext), false) {
            override fun sendKeyEvent(event: KeyEvent): Boolean { received.add(event); return true }
        }
        EditingBindings.send(connection, KeyEvent.KEYCODE_DPAD_LEFT, event(KeyEvent.META_FUNCTION_ON or KeyEvent.META_CTRL_ON))
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), received.map { it.action })
        assertTrue(received.all { it.isCtrlPressed && !it.isFunctionPressed })
    }
}
