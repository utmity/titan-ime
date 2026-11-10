package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

/** Activation policy for candidate taps on the strip and in the expanded
 * panel. Regression cover for two bugs: a grid tap during conversion used
 * to send ENTER and commit the whole composition, and a tap used to only
 * highlight, leaving even the chosen segment uncommitted. A tap must
 * resolve to the segment submit in both UIs. */
class CandidateActivationTest {
    @Test fun tapDuringConversionSubmitsOnlyTheFocusedSegment() {
        assertEquals(
            CandidateActivation.Action.SUBMIT_SEGMENT,
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
