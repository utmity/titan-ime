package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

/** Activation policy for the expanded candidate panel. Regression cover for
 * the bug where tapping a grid cell during conversion also sent ENTER to
 * Mozc and committed the whole composition. */
class CandidateActivationTest {
    @Test fun tapDuringConversionOnlyHighlights() {
        // The regression: a tap must never reach the commit action.
        assertEquals(
            CandidateActivation.Action.HIGHLIGHT,
            CandidateActivation.action(converted = true, explicitCommit = false)
        )
    }

    @Test fun enterDuringConversionHighlightsAndCommits() {
        assertEquals(
            CandidateActivation.Action.HIGHLIGHT_AND_COMMIT,
            CandidateActivation.action(converted = true, explicitCommit = true)
        )
    }

    @Test fun tapOnPredictionSubmits() {
        assertEquals(
            CandidateActivation.Action.SUBMIT,
            CandidateActivation.action(converted = false, explicitCommit = false)
        )
    }

    @Test fun enterOnPredictionSubmitsWithoutSeparateCommit() {
        assertEquals(
            CandidateActivation.Action.SUBMIT,
            CandidateActivation.action(converted = false, explicitCommit = true)
        )
    }
}
