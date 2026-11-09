package dev.utmity.titanime

/** A receipt for our own first write, not a guess from an editor's trailing text. */
internal data class InitialKeyHandoff(
    val packageName: String,
    val sourceField: Int,
    val letter: Char,
    val createdAt: Long,
    val physicalKey: Pair<Int, Int>,
    var targetField: Int? = null
) {
    fun live(now: Long) = now - createdAt in 0..1500
    fun matchesText(text: String, startOffset: Int, selectionStart: Int, selectionEnd: Int) =
        text == letter.toString() && startOffset == 0 && selectionStart == 1 && selectionEnd == 1
}
