package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.*

class CandidatePresentationTest {
    @Test fun firstSpaceUsesNativeCandidatesBeforeDesktopWindowOpens() {
        val engine = MozcEngine(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            engine.start()
            "nihongo".forEach { engine.key(KeyEvent.newBuilder().setKeyCode(it.code).build()) }
            val first = engine.key(KeyEvent.newBuilder().setSpecialKey(KeyEvent.SpecialKey.SPACE).build())
            assertTrue(first.preedit.hasHighlightedPosition())
            assertEquals("Desktop window not opened on first conversion", 0, first.candidateWindow.candidateCount)
            assertTrue(first.allCandidateWords.candidatesCount > 0)
            val displayed = CandidatePresentation.candidates(first)
            assertTrue(displayed.isNotEmpty() && displayed.size <= 8)
            assertTrue(displayed.all { shown -> first.allCandidateWords.candidatesList.any { it.id == shown.id && it.value == shown.value } })
            val chosen = displayed.first()
            val highlighted = engine.command(SessionCommand.CommandType.HIGHLIGHT_CANDIDATE, chosen.id)
            assertTrue(highlighted.preedit.hasHighlightedPosition())
            val committed = engine.command(SessionCommand.CommandType.SUBMIT)
            assertEquals(chosen.value, committed.result.value)
        } finally { engine.close() }
    }
}
