package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateAttribute
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateList
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateWord
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit

/**
 * The candidate strip skips its view rebuild when two consecutive
 * StripStates are equal, so equality must track exactly what is rendered:
 * candidate ids/values/annotations, focus, correction marks, preedit, the
 * raw-romaji row and the conversion flag.
 */
class CandidateStripStateTest {
    private fun word(id: Int, value: String, correction: Boolean = false): CandidateWord {
        val builder = CandidateWord.newBuilder().setId(id).setIndex(id).setValue(value)
        if (correction) builder.addAttributes(CandidateAttribute.TYPING_CORRECTION)
        return builder.build()
    }

    private fun output(words: List<CandidateWord>, focusedIndex: Int = 0, conversion: Boolean = true): Output {
        val builder = Output.newBuilder().setAllCandidateWords(
            CandidateList.newBuilder().addAllCandidates(words).setFocusedIndex(focusedIndex).build())
        if (conversion) builder.setPreedit(Preedit.newBuilder().setCursor(0).setHighlightedPosition(0).build())
        return builder.build()
    }

    private fun state(output: Output, preedit: String = "きょう", raw: String? = "kyou") =
        CandidatePresentation.stripState(output, preedit, raw, japanese = true,
            directField = false, inlineVisible = false, candidatesPanelOpen = false)

    @Test fun identicalOutputsProduceEqualStates() {
        val words = listOf(word(1, "今日"), word(2, "京"))
        assertEquals(state(output(words)), state(output(words)))
    }

    @Test fun changedCandidateOrFocusChangesState() {
        val base = listOf(word(1, "今日"), word(2, "京"))
        assertNotEquals(state(output(base)), state(output(listOf(word(1, "明日"), word(2, "京")))))
        assertNotEquals(state(output(base, focusedIndex = 0)), state(output(base, focusedIndex = 1)))
        assertEquals(1, state(output(base, focusedIndex = 0)).focusedId)
        assertEquals(2, state(output(base, focusedIndex = 1)).focusedId)
    }

    @Test fun correctionMarksArePartOfState() {
        val plain = state(output(listOf(word(1, "今日"))))
        val corrected = state(output(listOf(word(1, "今日", correction = true))))
        assertTrue(plain.correctionIds.isEmpty())
        assertEquals(setOf(1), corrected.correctionIds)
        assertNotEquals(plain, corrected)
    }

    @Test fun preeditRawAndConversionParticipate() {
        val out = output(listOf(word(1, "今日")))
        assertNotEquals(state(out, preedit = "きょう"), state(out, preedit = "あす"))
        assertNotEquals(state(out, raw = "kyou"), state(out, raw = null))
        assertTrue(state(out).conversion)
        assertFalse(state(output(listOf(word(1, "今日")), conversion = false)).conversion)
    }

    @Test fun defaultOutputHasEmptyState() {
        val empty = state(Output.getDefaultInstance(), preedit = "", raw = null)
        assertTrue(empty.candidateIds.isEmpty())
        assertFalse(empty.expandable)
        assertFalse(empty.conversion)
        assertNull(empty.focusedId)
    }
}
