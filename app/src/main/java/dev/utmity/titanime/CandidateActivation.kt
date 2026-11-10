package dev.utmity.titanime

/**
 * How activating one candidate is carried out, from the candidate strip or
 * the expanded panel.
 *
 * A tap commits only the chosen part, never the whole composition and
 * never a mere highlight: Mozc's SUBMIT_CANDIDATE selects the candidate
 * and commits the segments from the head up to the focused one, leaving
 * the remaining segments uncommitted so they can still be changed.
 * Before conversion starts the candidates are predictions, and the same
 * command submits the suggestion. Only an explicit commit request (the
 * expanded panel's Enter key) highlights the chosen candidate and then
 * commits the whole composition.
 */
internal object CandidateActivation {
    enum class Action { SUBMIT, SUBMIT_SEGMENT, HIGHLIGHT_AND_COMMIT }

    fun action(converted: Boolean, explicitCommit: Boolean): Action = when {
        !converted -> Action.SUBMIT
        explicitCommit -> Action.HIGHLIGHT_AND_COMMIT
        else -> Action.SUBMIT_SEGMENT
    }
}
