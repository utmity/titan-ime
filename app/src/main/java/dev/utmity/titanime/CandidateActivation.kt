package dev.utmity.titanime

/**
 * How activating one candidate from the expanded panel is carried out.
 *
 * A tap only selects, exactly like a tap on the candidate strip: while a
 * conversion is in progress the choice is highlighted into the focused
 * segment and the composition stays uncommitted, so the remaining segments
 * can still be changed. Only an explicit commit request (the panel's Enter
 * key) follows the highlight with a commit of the whole composition.
 * Before conversion starts the candidates are predictions; those are
 * submitted directly, again matching the strip.
 */
internal object CandidateActivation {
    enum class Action { SUBMIT, HIGHLIGHT, HIGHLIGHT_AND_COMMIT }

    fun action(converted: Boolean, explicitCommit: Boolean): Action = when {
        !converted -> Action.SUBMIT
        explicitCommit -> Action.HIGHLIGHT_AND_COMMIT
        else -> Action.HIGHLIGHT
    }
}
