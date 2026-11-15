package dev.utmity.titanime

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config

/** Input-table settings, kept separate from suggestion preferences. */
internal object MozcInputProfile {
    fun request(twelveKey: Boolean): Request = Request.newBuilder()
        .setKanaModifierInsensitiveConversion(true)
        .apply {
            if (twelveKey) specialRomanjiTable = Request.SpecialRomanjiTable.TWELVE_KEYS_TO_HIRAGANA
        }.build()

    fun configure(config: Config.Builder, twelveKey: Boolean) {
        if (twelveKey) config.composingTimeoutThresholdMsec = 0
    }
}
