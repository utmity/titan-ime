package dev.utmity.titanime

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PersistableBundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ClipboardPanelDeviceTest {
    @Test fun extractsOnlyLiteralSingleTextItemsAndKeepsSensitivityAndOriginalNewlines() {
        val raw = "  a\r\n\r\nb\n "
        val clip = ClipData.newPlainText("test", raw)
        assertEquals(CurrentClipboard.Text(raw, false), ClipboardTextReader.fromClip(clip))
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        assertEquals(CurrentClipboard.Text(raw, true), ClipboardTextReader.fromClip(clip))
        assertFalse(ClipboardPanel.rows(ClipboardTextReader.fromClip(clip), emptyList()).single().label().contains(raw))
        assertEquals(CurrentClipboard.Empty, ClipboardTextReader.fromClip(null))
        assertEquals(CurrentClipboard.Unsupported, ClipboardTextReader.fromClip(ClipData.newRawUri("image", Uri.parse("content://test/image"))))
        assertEquals(CurrentClipboard.Unsupported, ClipboardTextReader.fromClip(ClipData.newIntent("intent", Intent("test"))))
        val mixed = ClipData("mixed", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item("caption", null, Intent("test"), Uri.parse("content://test/item")))
        assertEquals(CurrentClipboard.Unsupported, ClipboardTextReader.fromClip(mixed))
        clip.addItem(ClipData.Item("second"))
        assertEquals(CurrentClipboard.Unsupported, ClipboardTextReader.fromClip(clip))
    }

    @Test fun historyStorageRetainsFullTextAndExistingLimitsPinsAndPrivacyRules() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "clipboard-panel-test-${System.nanoTime()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(ignored: String, mode: Int): SharedPreferences = context.getSharedPreferences(name, mode)
        }
        try {
            val history = ClipboardHistory(isolated)
            val raw = "  line1\r\n\r\nline3\n  "
            history.capture(raw, false)
            assertTrue(history.entries().isEmpty())
            history.enabled = true
            history.capture(raw, false)
            history.capture(raw, false)
            assertEquals(raw, history.entries().single().text)
            assertEquals(raw, ClipboardHistory(isolated).entries().single().text)
            history.capture("secret", true)
            history.capture("x".repeat(10001), false)
            history.capture("\r\n  ", false)
            assertEquals(1, history.entries().size)
            val before = history.entries()
            val current = ClipboardPanel.rows(CurrentClipboard.Text("not-saved", false), before.map { ClipboardPanel.Saved(it.text, it.pinned) })
            assertFalse(current.first().stored)
            assertEquals(before, history.entries())
            history.pin(raw)
            history.limit = 5
            repeat(8) { history.capture("entry-$it", false) }
            assertEquals(5, history.entries().size)
            assertTrue(history.entries().first { it.text == raw }.pinned)
            history.delete(raw)
            assertFalse(history.entries().any { it.text == raw })
            history.clear()
            assertTrue(history.entries().isEmpty())
        } finally { context.deleteSharedPreferences(name) }
    }
}
