package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class InputModeTest {
    @Test fun newAndLegacyInstallsDefaultToQwertyAndEverySelectionRoundTrips() {
        assertEquals(InputMode.QWERTY, InputMode.restore(null))
        assertEquals(InputMode.QWERTY, InputMode.restore("unknown-mode"))
        InputMode.entries.forEach { assertEquals(it, InputMode.restore(it.name)) }
        assertTrue(InputMode.QWERTY.japanese(true))
        assertFalse(InputMode.QWERTY.japanese(false))
        for (mode in listOf(InputMode.KANA, InputMode.ENGLISH)) {
            assertTrue(mode.usesTwelveKeyTable)
            assertTrue(mode.japanese(false))
        }
        for (mode in listOf(InputMode.NUMBER_HALF, InputMode.NUMBER_FULL)) {
            assertFalse(mode.usesTwelveKeyTable)
            assertFalse(mode.japanese(true))
        }
    }

    @Test fun pCyclesOnlyTwelveKeyModesAndNeverLeavesQwerty() {
        var mode = InputMode.KANA
        repeat(3) {
            for (expected in listOf(InputMode.ENGLISH, InputMode.NUMBER_HALF,
                InputMode.NUMBER_FULL, InputMode.KANA)) {
                mode = mode.nextTwelveKey()
                assertEquals(expected, mode)
            }
        }
        assertEquals(InputMode.QWERTY, InputMode.QWERTY.nextTwelveKey())
    }

    @Test fun statusDistinguishesActualTwelveKeyModeFromQwertyLanguagePreference() {
        for (mode in InputMode.entries.filter { it != InputMode.QWERTY }) {
            assertEquals(mode.description, mode.status(false))
            assertEquals(mode.description, mode.status(true))
        }
        assertEquals("QWERTY・日本語", InputMode.QWERTY.status(true))
        assertEquals("QWERTY・英語", InputMode.QWERTY.status(false))
    }

    @Test fun modesCycleIncludingQwerty() {
        var mode = InputMode.QWERTY
        val expected = listOf(
            InputMode.KANA, InputMode.ENGLISH,
            InputMode.NUMBER_HALF, InputMode.NUMBER_FULL,
            InputMode.QWERTY
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
                TwelveKeyNumericSymbols.choices(key).first()
            )
        }
    }

    @Test fun fullwidthNumbersToggleGboardLikeSymbolsWithoutTimeLimit() {
        val cycle = TwelveKeyNumericCycle()
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
        val cycle = TwelveKeyNumericCycle()
        assertEquals("（", cycle.advance('*'))
        assertEquals("）", cycle.advance('*'))
        assertEquals("、", cycle.advance('#'))
        assertEquals("。", cycle.advance('#'))
        cycle.reset()
        assertEquals("、", cycle.advance('#'))
        assertEquals("、", cycle.current)
    }
}
