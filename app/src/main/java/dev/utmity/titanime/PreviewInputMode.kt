package dev.utmity.titanime

/**
 * Preview-only input layouts. Production Titan IME still uses its existing
 * Japanese/English QWERTY language state and shortcuts.
 */
internal enum class PreviewInputMode(val badge: String, val description: String) {
    QWERTY("Q", "通常QWERTY"),
    KANA("あ", "12キーかな"),
    ENGLISH("abc", "12キー英字"),
    NUMBER_HALF("123", "12キー数字・半角"),
    NUMBER_FULL("１２３", "12キー数字・全角と記号");

    fun next(): PreviewInputMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Gboard Japanese 12-key numeric key's printed symbol groups, in the
 * display order observed on that layout. These can vary with Gboard version.
 *
 * For a physical toggle keyboard, each row key cycles from the full-width
 * digit through these alternatives, with NO time-based timeout.
 */
internal object PreviewNumericSymbols {
    private val hints = mapOf(
        '1' to listOf("☆", "♪", "→"),
        '2' to listOf("￥", "＄", "€"),
        '3' to listOf("％", "＃"),
        '4' to listOf("○", "＊", "・"),
        '5' to listOf("＋", "×", "÷"),
        '6' to listOf("＜", "＝", "＞"),
        '7' to listOf("「", "」", "："),
        '8' to listOf("〒", "々", "〆"),
        '9' to listOf("＾", "｜", "＼"),
        '0' to listOf("～", "…", "＠"),
    )

    fun choices(key: Char): List<String> {
        if (key !in '0'..'9') return emptyList()
        val digit = (key - '0' + '０'.code).toChar().toString()
        return listOf(digit) + (hints[key] ?: emptyList())
    }
}

/** Pure stepping state: editor ownership/selection stays in InputConnection. */
internal class PreviewNumericCycle {
    var key: Char? = null
        private set
    var index: Int = 0
        private set

    fun reset() { key = null; index = 0 }

    fun advance(pressed: Char): String {
        val values = PreviewNumericSymbols.choices(pressed)
        require(values.isNotEmpty()) { "Not a numeric key: $pressed" }
        index = if (key == pressed) (index + 1) % values.size else 0
        key = pressed
        return values[index]
    }
}
