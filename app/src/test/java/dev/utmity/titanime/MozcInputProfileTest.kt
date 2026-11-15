package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config

class MozcInputProfileTest {
    @Test fun previewSelectsStockTableAndDisablesPersistedTimeout() {
        val config = Config.newBuilder().setComposingTimeoutThresholdMsec(1000)
            .setCustomRomanTable(com.google.protobuf.ByteString.copyFromUtf8("custom qwerty table"))
        MozcInputProfile.configure(config, true)
        assertEquals(0, config.composingTimeoutThresholdMsec)
        assertEquals("custom qwerty table", config.customRomanTable.toStringUtf8())
        assertEquals(Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA,
            MozcInputProfile.request(true).specialRomanjiTable)
    }

    @Test fun englishPreviewUsesBuiltInAscii12KeyTable() {
        assertEquals(Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HALFWIDTHASCII,
            MozcInputProfile.request(true, ascii = true).specialRomanjiTable)
        assertEquals(Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA,
            MozcInputProfile.request(true, ascii = false).specialRomanjiTable)
    }

    @Test fun qwertyPreservesConfigAndOriginalRequest() {
        val config = Config.newBuilder().setComposingTimeoutThresholdMsec(1234)
        val before = config.build()
        MozcInputProfile.configure(config, false)
        assertEquals(before, config.build())
        val original = Request.newBuilder().setKanaModifierInsensitiveConversion(true).build()
        assertEquals(original, MozcInputProfile.request(false))
        assertFalse(MozcInputProfile.request(false).hasSpecialRomanjiTable())
    }
}
