package dev.utmity.titanime

import java.text.Normalizer

/** Deliberately narrow mappings: unrelated symbols and supplementary characters survive. */
internal object SelectionTransforms {
    val labels = listOf("英字を大文字", "英字を小文字", "英字の大小反転", "英数字を半角", "英数字を全角",
        "ひらがな→カタカナ", "カタカナ→ひらがな", "カタカナを全角", "カタカナを半角")
    private val halfToFull = (0xff61..0xff9f).associate { cp ->
        cp.toChar().toString() to Normalizer.normalize(cp.toChar().toString(), Normalizer.Form.NFKC)
    }
    private val fullToHalf = halfToFull.entries.associate { (half, full) -> full to half }
    fun apply(text: String, kind: Int): String = when (kind) {
        0, 1, 2 -> text.map { c -> when {
            c in 'a'..'z' && kind != 1 -> c.uppercaseChar()
            c in 'A'..'Z' && kind != 0 -> c.lowercaseChar()
            else -> c
        } }.joinToString("")
        3 -> text.map { if (it in 'Ａ'..'Ｚ' || it in 'ａ'..'ｚ' || it in '０'..'９') (it.code - 0xfee0).toChar() else it }.joinToString("")
        4 -> text.map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9') (it.code + 0xfee0).toChar() else it }.joinToString("")
        5 -> text.map { if (it in '\u3041'..'\u3096') (it.code + 0x60).toChar() else it }.joinToString("")
        6 -> text.map { if (it in '\u30a1'..'\u30f6') (it.code - 0x60).toChar() else it }.joinToString("")
        7 -> Regex("[\\uFF61-\\uFF9F]+").replace(text) { Normalizer.normalize(it.value, Normalizer.Form.NFKC) }
        8 -> buildString {
            text.forEach { c ->
                val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                if (decomposed.all { fullToHalf.containsKey(it.toString()) }) {
                    decomposed.forEach { append(fullToHalf.getValue(it.toString())) }
                } else append(fullToHalf[c.toString()] ?: c.toString())
            }
        }
        else -> text
    }
}
