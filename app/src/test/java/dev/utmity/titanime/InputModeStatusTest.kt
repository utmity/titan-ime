package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class InputModeStatusTest {
    @Test fun notifyOnlyTransitionsAndStopNotifyingRemovedPages() {
        val original = InputModeStatus.active
        val seen = mutableListOf<String?>()
        val observer: () -> Unit = { seen += InputModeStatus.active }
        try {
            InputModeStatus.active = null
            InputModeStatus.observe(observer)
            InputModeStatus.active = "12キーかな"
            InputModeStatus.active = "12キーかな"
            InputModeStatus.active = "12キー英字"
            InputModeStatus.active = null
            assertEquals(listOf("12キーかな", "12キー英字", null), seen)
            InputModeStatus.removeObserver(observer)
            InputModeStatus.active = "QWERTY・英語"
            assertEquals(3, seen.size)
        } finally {
            InputModeStatus.removeObserver(observer)
            InputModeStatus.active = original
        }
    }
}
