package dev.utmity.titanime

/**
 * Small, deterministic romaji-to-hiragana composer.
 * The unfinished suffix remains pending until more input arrives.
 * This is an initial prototype, not a complete Hepburn/Kunrei implementation.
 */
class RomajiComposer {
    private val syllables = mapOf(
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "shi" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "chi" to "ち", "tsu" to "つ", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を", "nn" to "ん",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "za" to "ざ", "ji" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "da" to "だ", "de" to "で", "do" to "ど",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ"
    )
    private val prefixes = syllables.keys.flatMap { key ->
        (1 until key.length).map { key.substring(0, it) }
    }.toSet()
    private val consonants = "bcdfghjklmpqrstvwxyz"
    var composed: String = ""
        private set
    var pending: String = ""
        private set

    fun type(char: Char) {
        val c = char.lowercaseChar()
        if (c !in 'a'..'z') { flush(); composed += char; return }
        pending += c
        resolve()
    }

    private fun resolve() {
        while (pending.isNotEmpty()) {
            // Defer nn until the following letter distinguishes nni from nn+k.
            if (pending == "nn") return
            if (pending.startsWith("nn") && pending.length > 2) {
                composed += "ん"
                pending = pending.drop(if (pending[2] in "aiueoy") 1 else 2)
                continue
            }
            if (pending.length >= 2 && pending[0] == pending[1] &&
                pending[0] in consonants && pending[0] != 'n') {
                composed += "っ"
                pending = pending.drop(1)
                continue
            }
            if (pending.startsWith("n") && pending.length >= 2 &&
                pending[1] !in "aiueoyn") {
                composed += "ん"
                pending = pending.drop(1)
                continue
            }
            val match = syllables[pending]
            if (match != null && pending !in prefixes) {
                composed += match
                pending = ""
                return
            }
            if (pending in prefixes || pending == "n") return
            if (match != null) {
                composed += match
                pending = ""
                return
            }
            composed += pending.first()
            pending = pending.drop(1)
        }
    }

    fun backspace() {
        if (pending.isNotEmpty()) pending = pending.dropLast(1)
        else if (composed.isNotEmpty()) composed = composed.dropLast(1)
    }

    fun flush() {
        if (pending == "n" || pending == "nn") composed += "ん"
        else composed += pending
        pending = ""
    }

    fun text(): String = composed + pending
    fun clear() { composed = ""; pending = "" }
}
