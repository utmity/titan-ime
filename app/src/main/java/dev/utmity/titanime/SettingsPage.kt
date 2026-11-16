package dev.utmity.titanime

/** Explicit navigation model, independent of setting labels or persisted keys. */
internal enum class SettingsPage(val title: String) {
    HOME("設定"), QWERTY("QWERTY入力設定"), TWELVE_KEY("ガラケー入力（12キー）設定"),
    COMMON("共通設定"), GUIDE("操作ガイド"), QWERTY_GUIDE("QWERTYの操作ガイド"),
    TWELVE_KEY_GUIDE("12キーの操作ガイド");

    companion object {
        fun homePages(): List<SettingsPage> = listOf(QWERTY, TWELVE_KEY, COMMON, GUIDE)
        fun resolve(name: String?): SettingsPage = entries.firstOrNull { it.name == name } ?: HOME
    }
}
