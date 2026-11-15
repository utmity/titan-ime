package dev.utmity.titanime

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewInputModeTest {
    @Test fun pCyclesOnlyTwelveKeyModesAndNeverLeavesQwerty() {
        var mode = PreviewInputMode.KANA
        repeat(3) {
            for (expected in listOf(PreviewInputMode.ENGLISH, PreviewInputMode.NUMBER_HALF,
                PreviewInputMode.NUMBER_FULL, PreviewInputMode.KANA)) {
                mode = mode.nextTwelveKey()
                assertEquals(expected, mode)
            }
        }
        assertEquals(PreviewInputMode.QWERTY, PreviewInputMode.QWERTY.nextTwelveKey())
    }

    @Test fun statusDistinguishesActualTwelveKeyModeFromQwertyLanguagePreference() {
        for (mode in PreviewInputMode.entries.filter { it != PreviewInputMode.QWERTY }) {
            assertEquals(mode.description, mode.status(false))
            assertEquals(mode.description, mode.status(true))
        }
        assertEquals("QWERTY・日本語", PreviewInputMode.QWERTY.status(true))
        assertEquals("QWERTY・英語", PreviewInputMode.QWERTY.status(false))
    }

    @Test fun modesCycleIncludingQwerty() {
        var mode = PreviewInputMode.QWERTY
        val expected = listOf(
            PreviewInputMode.KANA, PreviewInputMode.ENGLISH,
            PreviewInputMode.NUMBER_HALF, PreviewInputMode.NUMBER_FULL,
            PreviewInputMode.QWERTY
        )
        expected.forEach { next ->
            mode = mode.next()
            assertEquals(next, mode)
        }
    }

    @Test fun fullwidthNumericKeysStartWithFullwidthDigits() {
        "1234567890".forEach { key ->
            assertEquals(
                (key - '0' + '０'.code).toChar().toString(),
                PreviewNumericSymbols.choices(key).first()
            )
        }
    }

    @Test fun fullwidthNumbersToggleGboardLikeSymbolsWithoutTimeLimit() {
        val cycle = PreviewNumericCycle()
        assertEquals("２", cycle.advance('2'))
        assertEquals("￥", cycle.advance('2'))
        assertEquals("＄", cycle.advance('2'))
        assertEquals("€", cycle.advance('2'))
        assertEquals("２", cycle.advance('2'))
        assertEquals("５", cycle.advance('5'))
        assertEquals("＋", cycle.advance('5'))
        assertEquals("×", cycle.advance('5'))
        assertEquals("÷", cycle.advance('5'))
        assertEquals("０", cycle.advance('0'))
        assertEquals("～", cycle.advance('0'))
        assertEquals("…", cycle.advance('0'))
        assertEquals("＠", cycle.advance('0'))
        assertEquals("０", cycle.advance('0'))
    }

    @Test fun punctuationAndReset() {
        val cycle = PreviewNumericCycle()
        assertEquals("（", cycle.advance('*'))
        assertEquals("）", cycle.advance('*'))
        assertEquals("、", cycle.advance('#'))
        assertEquals("。", cycle.advance('#'))
        cycle.reset()
        assertEquals("、", cycle.advance('#'))
        assertEquals("、", cycle.current)
    }
}
