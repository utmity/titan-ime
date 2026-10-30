package dev.utmity.titanime

internal object VoiceLanguagePolicy {
    fun primary(preference: String, deviceLanguage: String) = when (preference) {
        "ja" -> "ja-JP"
        "en" -> "en-US"
        else -> deviceLanguage.ifBlank { "ja-JP" }
    }
    // Android ERROR_CLIENT / LANGUAGE_NOT_SUPPORTED / LANGUAGE_UNAVAILABLE.
    // Retry once without optional switching, never loop on network/mic/no-match errors.
    fun retryWithoutSwitch(automatic: Boolean, error: Int) = automatic && error in setOf(5, 12, 13)
}
