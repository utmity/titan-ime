package dev.utmity.titanime

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config

/** Input-table settings, kept separate from suggestion preferences. */
internal object MozcInputProfile {
    fun request(twelveKey: Boolean, ascii: Boolean = false): Request = Request.newBuilder()
        .setKanaModifierInsensitiveConversion(true)
        .apply {
            if (twelveKey) specialRomanjiTable = if (ascii)
                Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HALFWIDTHASCII
                else Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA
        }.build()

    fun configure(config: Config.Builder, twelveKey: Boolean) {
        if (twelveKey) config.composingTimeoutThresholdMsec = 0
    }
}
