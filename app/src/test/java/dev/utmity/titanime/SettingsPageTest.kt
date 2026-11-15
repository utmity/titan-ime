package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class SettingsPageTest {
    @Test fun bothPackagesUseAllPagesAndSupportRestoration() {
        assertEquals(listOf(SettingsPage.QWERTY, SettingsPage.TWELVE_KEY, SettingsPage.COMMON, SettingsPage.GUIDE), SettingsPage.homePages())
        SettingsPage.entries.forEach { assertEquals(it, SettingsPage.resolve(it.name)) }
        assertEquals(SettingsPage.HOME, SettingsPage.resolve("invalid"))
        assertEquals(SettingsPage.HOME, SettingsPage.resolve(null))
    }
}
