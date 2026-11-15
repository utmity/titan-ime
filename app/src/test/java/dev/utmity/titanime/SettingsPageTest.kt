package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class SettingsPageTest {
    @Test fun productionHasNoTwelveKeyPageOrDeepLink() {
        assertEquals(listOf(SettingsPage.QWERTY, SettingsPage.COMMON, SettingsPage.GUIDE), SettingsPage.homePages(false))
        assertEquals(SettingsPage.HOME, SettingsPage.resolve("TWELVE_KEY", false))
        assertEquals(SettingsPage.HOME, SettingsPage.resolve("TWELVE_KEY_GUIDE", false))
    }
    @Test fun previewPagesResolveWithoutDuplicateEntries() {
        val pages = SettingsPage.homePages(true)
        assertTrue(pages.contains(SettingsPage.TWELVE_KEY))
        assertEquals(pages.size, pages.distinct().size)
        SettingsPage.entries.forEach { assertEquals(it, SettingsPage.resolve(it.name, true)) }
        assertEquals(SettingsPage.HOME, SettingsPage.resolve("invalid", true))
        assertEquals(SettingsPage.HOME, SettingsPage.resolve(null, true))
    }
}
