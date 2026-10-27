package dev.utmity.titanime

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidateWindow.CandidateWord

/** Mozc's desktop candidate-window visibility is not Android strip visibility. */
internal object CandidatePresentation {
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
}
