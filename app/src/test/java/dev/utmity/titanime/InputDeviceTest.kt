package dev.utmity.titanime
import org.junit.Test
import org.junit.Assert.assertEquals
class InputDeviceTest {
    @Test fun testComposer() {
        val composer = RomajiComposer()
        "konnichiha".forEach(composer::type)
        assertEquals("こんにちは", composer.text())
        composer.clear()
        "kitte".forEach(composer::type)
        assertEquals("きって", composer.text())
        composer.clear(); composer.type('n'); composer.flush()
        assertEquals("ん", composer.text())
        for ((input, expected) in mapOf("nn" to "ん", "nna" to "んな", "nnka" to "んか", "kanna" to "かんな")) {
            composer.clear()
            input.forEach(composer::type)
            composer.flush()
            assertEquals(input, expected, composer.text())
        }
    }
}
