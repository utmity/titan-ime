package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class VoiceLanguagePolicyTest {
    @Test fun preferredAndDeviceLanguage() {
        assertEquals("ja-JP", VoiceLanguagePolicy.primary("ja", "en-GB"))
        assertEquals("en-US", VoiceLanguagePolicy.primary("en", "ja-JP"))
        assertEquals("en-GB", VoiceLanguagePolicy.primary("auto", "en-GB"))
    }
    @Test fun unsupportedSwitchRetriesOnlyOnce() {
        for (error in listOf(5, 12, 13)) {
            assertTrue(VoiceLanguagePolicy.retryWithoutSwitch(true, error))
            assertFalse(VoiceLanguagePolicy.retryWithoutSwitch(false, error))
        }
        for (error in listOf(1, 2, 6, 7, 9)) assertFalse(VoiceLanguagePolicy.retryWithoutSwitch(true, error))
    }
}
