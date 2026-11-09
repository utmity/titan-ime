package dev.utmity.titanime

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionTransformsTest {
    @Test fun latinAndWidthPreserveUnrelatedText() {
        assertEquals("HELLO 日本😀\n", SelectionTransforms.apply("hello 日本😀\n", 0))
        assertEquals("hello", SelectionTransforms.apply("HELLO", 1))
        assertEquals("hEllO", SelectionTransforms.apply("HeLLo", 2))
        assertEquals("ABC123！①", SelectionTransforms.apply("ＡＢＣ１２３！①", 3))
        assertEquals("ＡＢＣ１２３!①", SelectionTransforms.apply("ABC123!①", 4))
    }
    @Test fun kanaVoicingAndSmallLetters() {
        assertEquals("コンニチハ", SelectionTransforms.apply("こんにちは", 5))
        assertEquals("こんにちは", SelectionTransforms.apply("コンニチハ", 6))
        assertEquals("ガパヴァッー😀", SelectionTransforms.apply("ｶﾞﾊﾟｳﾞｧｯｰ😀", 7))
        assertEquals("ｶﾞﾊﾟｳﾞｧｯｰ😀", SelectionTransforms.apply("ガパヴァッー😀", 8))
        assertEquals("ｶﾞ", SelectionTransforms.apply("カ\u3099", 8))
        assertEquals("ヵヶﾜﾞ", SelectionTransforms.apply("ヵヶヷ", 8))
        assertEquals("①ﬁ", SelectionTransforms.apply("①ﬁ", 7))
    }
}
