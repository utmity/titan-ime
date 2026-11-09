package dev.utmity.titanime

/** Tracks the user's exact keys, not a romanization reconstructed from kana. */
internal class RawRomajiTrace {
    private var keys: String? = null
    val candidate: String? get() = keys?.takeIf { it.isNotEmpty() }

    fun append(character: Char, newComposition: Boolean) {
        if (character !in 'a'..'z' && character !in 'A'..'Z') {
            invalidate()
            return
        }
        keys = if (newComposition) character.toString()
        else keys?.takeIf { it.length < 128 }?.plus(character)
    }

    fun invalidate() { keys = null }
}
