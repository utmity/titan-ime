package dev.utmity.titanime

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateWord

/** Mozc's desktop candidate-window visibility is not Android strip visibility. */
internal object CandidatePresentation {
    fun expanded(output: Output): List<CandidateWord> =
        if (output.allCandidateWords.candidatesCount > 0) output.allCandidateWords.candidatesList else candidates(output)
    fun candidates(output: Output): List<CandidateWord> {
        if (output.candidateWindow.candidateCount > 0) return output.candidateWindow.candidateList.map {
            CandidateWord.newBuilder().setId(it.id).setIndex(it.index).setValue(it.value)
                .setAnnotation(it.annotation).build()
        }
        return if (output.preedit.hasHighlightedPosition()) output.allCandidateWords.candidatesList.take(8) else emptyList()
    }
    fun focusedId(output: Output): Int? {
        val window = output.candidateWindow
        if (window.hasFocusedIndex()) return window.candidateList.firstOrNull { it.index == window.focusedIndex }?.id
        val all = output.allCandidateWords
        return if (all.hasFocusedIndex()) all.candidatesList.getOrNull(all.focusedIndex)?.id else null
    }

    /**
     * Everything the candidate strip would render, as plain comparable values.
     * The strip compares the previous state with the next one and skips the
     * view rebuild entirely when nothing visible changed; equality here is
     * far cheaper than detaching and recreating the row's views per keystroke.
     */
    internal data class StripState(
        val candidateIds: List<Int>,
        val candidateValues: List<String>,
        val candidateAnnotations: List<String>,
        val focusedId: Int?,
        val correctionIds: Set<Int>,
        val preedit: String,
        val raw: String?,
        val japanese: Boolean,
        val directField: Boolean,
        val inlineVisible: Boolean,
        val expandable: Boolean,
        val conversion: Boolean,
        val candidatesPanelOpen: Boolean
    )

    fun stripState(output: Output, preedit: String, raw: String?, japanese: Boolean,
        directField: Boolean, inlineVisible: Boolean, candidatesPanelOpen: Boolean): StripState {
        val candidates = candidates(output)
        val corrections = candidates.mapNotNull { candidate ->
            val attributes = output.allCandidateWords.candidatesList
                .firstOrNull { it.id == candidate.id }?.attributesList.orEmpty()
            if (attributes.any { it.name == "SPELLING_CORRECTION" || it.name == "TYPING_CORRECTION" }) candidate.id else null
        }.toSet()
        return StripState(
            candidateIds = candidates.map { it.id },
            candidateValues = candidates.map { it.value },
            candidateAnnotations = candidates.map { it.annotation.description },
            focusedId = focusedId(output),
            correctionIds = corrections,
            preedit = preedit,
            raw = raw,
            japanese = japanese,
            directField = directField,
            inlineVisible = inlineVisible,
            expandable = expanded(output).size > 1,
            conversion = output.preedit.hasHighlightedPosition(),
            candidatesPanelOpen = candidatesPanelOpen
        )
    }
}
