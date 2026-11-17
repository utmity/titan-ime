package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class ClipboardPanelTest {
    @Test fun singleLineAndShortWhitespaceRemainReadable() {
        assertEquals(ClipboardPreview.Preview("  sudo apt update  ", 1), ClipboardPreview.of("  sudo apt update  "))
        assertEquals(ClipboardPreview.Preview("", 1), ClipboardPreview.of(""))
        assertEquals(ClipboardPreview.Preview("   ", 1), ClipboardPreview.of("   "))
    }

    @Test fun lfCrLfCrAndEmptyTrailingLinesAreCountedWithoutDoubleCountingCrLf() {
        for (raw in listOf("a\nb\nc", "a\r\nb\r\nc", "a\rb\rc")) {
            assertEquals(ClipboardPreview.Preview("a ↵ b ↵ c", 3), ClipboardPreview.of(raw))
        }
        assertEquals(ClipboardPreview.Preview(" ↵ a ↵  ↵ ", 4), ClipboardPreview.of("\r\na\n\n"))
        assertEquals(ClipboardPreview.Preview(" ↵ ", 2), ClipboardPreview.of("\n"))
    }

    @Test fun longPreviewIsBoundedWhileFullTextAndWhitespaceStayIntact() {
        val original = "  sudo apt update\r\n\r\n" + "x".repeat(20000) + "\n  reboot  \n"
        val row = ClipboardPanel.rows(CurrentClipboard.Text(original, false), emptyList()).single()
        val preview = ClipboardPreview.of(row.text)
        assertEquals(5, preview.lines)
        assertTrue(preview.text.endsWith("…"))
        assertTrue(preview.text.length <= 361)
        assertTrue(row.label().contains("[5行]"))
        assertEquals(original, row.text)
        assertTrue(row.text.endsWith("\n  reboot  \n"))
    }

    @Test fun unicodeTruncationDoesNotSplitSurrogatePairs() {
        assertEquals("😀😀…", ClipboardPreview.of("😀😀😀", 2).text)
        assertFalse(ClipboardPreview.of("ab", 2).text.endsWith("…"))
    }

    @Test fun currentClipIsFirstAndDuplicateSavedItemBecomesOneManageableRow() {
        val saved = listOf(ClipboardPanel.Saved("pinned", true), ClipboardPanel.Saved("same", true), ClipboardPanel.Saved("older", false))
        val rows = ClipboardPanel.rows(CurrentClipboard.Text("same", false), saved)
        assertEquals(listOf("same", "pinned", "older"), rows.map { it.text })
        assertTrue(rows.first().current)
        assertTrue(rows.first().stored)
        assertTrue(rows.first().pinned)
        assertEquals(3, saved.size) // Building the view never rewrites history.
        assertEquals(saved, listOf(ClipboardPanel.Saved("pinned", true), ClipboardPanel.Saved("same", true), ClipboardPanel.Saved("older", false)))
    }

    @Test fun sensitiveCurrentClipMasksEvenAnIdenticalOldHistoryEntry() {
        val secret = "secret\nvalue"
        val rows = ClipboardPanel.rows(CurrentClipboard.Text(secret, true), listOf(ClipboardPanel.Saved(secret, false)))
        assertEquals(1, rows.size)
        assertFalse(rows.single().label().contains("secret"))
        assertFalse(rows.single().label().contains("value"))
        assertFalse(rows.single().label().contains("2行"))
        assertEquals(secret, rows.single().text) // Only the explicit paste payload holds the raw value.
    }

    @Test fun disabledOrEmptyHistoryStillOffersCurrentClipWithoutSavingIt() {
        val row = ClipboardPanel.rows(CurrentClipboard.Text("current", false), emptyList()).single()
        assertTrue(row.current)
        assertFalse(row.stored)
        assertEquals("current", row.text)
        for (state in listOf(CurrentClipboard.Empty, CurrentClipboard.Unavailable, CurrentClipboard.Unsupported)) {
            assertEquals(emptyList<ClipboardPanel.Row>(), ClipboardPanel.rows(state, emptyList()))
            assertEquals("saved", ClipboardPanel.rows(state, listOf(ClipboardPanel.Saved("saved", false))).single().text)
        }
    }
}
