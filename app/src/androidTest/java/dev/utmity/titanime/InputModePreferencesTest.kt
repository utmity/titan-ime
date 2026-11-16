package dev.utmity.titanime

import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class InputModePreferencesTest {
    @Test fun legacyQwertyDefaultAndModeRoundTripPreserveExistingSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val preferences = ImePreferences(instrumentation.targetContext)
            val store = preferences.store
            val original = store.getString("input_mode", null)
            val originalLast = store.getString("last_twelve_key_mode", null)
            try {
                check(store.edit().remove("input_mode").commit())
                val legacy = store.all.filterKeys { it != "last_twelve_key_mode" }
                assertEquals(InputMode.QWERTY, preferences.inputMode)
                assertFalse(store.contains("input_mode")) // No implicit migration/write.
                val otherInstance = ImePreferences(instrumentation.targetContext)
                InputMode.entries.forEach { mode ->
                    preferences.inputMode = mode
                    assertEquals(mode, otherInstance.inputMode)
                    assertEquals(mode, ImePreferences(instrumentation.targetContext).inputMode)
                    assertEquals(legacy, store.all.filterKeys { it != "input_mode" && it != "last_twelve_key_mode" })
                }
                check(store.edit().putString("input_mode", "future-mode").commit())
                assertEquals(InputMode.QWERTY, preferences.inputMode)
                assertEquals("future-mode", store.getString("input_mode", null))
                check(store.edit().remove("input_mode").commit())
                assertEquals(InputMode.QWERTY, otherInstance.inputMode)
            } finally {
                check(store.edit().apply {
                    if (original == null) remove("input_mode") else putString("input_mode", original)
                    if (originalLast == null) remove("last_twelve_key_mode") else putString("last_twelve_key_mode", originalLast)
                }.commit())
            }
        }
    }

    @Test fun remembersEveryTwelveKeyModeThroughQwertyAndReloadWithoutChangingOtherSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val prefs = ImePreferences(instrumentation.targetContext)
            val store = prefs.store
            val keys = setOf("input_mode", "last_twelve_key_mode")
            val original = keys.associateWith { store.getString(it, null) }
            val unrelated = store.all.filterKeys { it !in keys }
            try {
                check(store.edit().remove("input_mode").remove("last_twelve_key_mode").commit())
                assertEquals(InputMode.QWERTY, prefs.inputMode)
                assertEquals(InputMode.KANA, prefs.lastTwelveKeyMode)
                assertTrue(keys.none { store.contains(it) })
                val other = ImePreferences(instrumentation.targetContext)
                for (mode in InputMode.entries.filter { it != InputMode.QWERTY }) {
                    prefs.inputMode = mode
                    prefs.inputMode = InputMode.QWERTY
                    assertEquals(mode, prefs.lastTwelveKeyMode)
                    assertEquals(mode, other.lastTwelveKeyMode)
                    val reloaded = ImePreferences(instrumentation.targetContext)
                    assertEquals(InputMode.QWERTY, reloaded.inputMode)
                    assertEquals(mode, reloaded.lastTwelveKeyMode)
                    assertEquals(mode, reloaded.inputMode.toggleFamily(reloaded.lastTwelveKeyMode))
                    assertEquals(unrelated, store.all.filterKeys { it !in keys })
                }
                // Upgrade from main's old single-mode setting without a last-mode key.
                check(store.edit().remove("last_twelve_key_mode").putString("input_mode", "ENGLISH").commit())
                val legacy = ImePreferences(instrumentation.targetContext)
                assertEquals(InputMode.ENGLISH, legacy.lastTwelveKeyMode)
                legacy.inputMode = InputMode.QWERTY
                assertEquals(InputMode.ENGLISH, ImePreferences(instrumentation.targetContext).lastTwelveKeyMode)
                check(store.edit().remove("last_twelve_key_mode").putString("input_mode", "ENGLISH").commit())
                assertEquals(InputMode.ENGLISH, other.lastTwelveKeyMode)
                check(store.edit().remove("input_mode").commit())
                assertEquals(InputMode.KANA, other.lastTwelveKeyMode)
                assertEquals(ImePreferences(instrumentation.targetContext).lastTwelveKeyMode, other.lastTwelveKeyMode)
            } finally {
                check(store.edit().apply { original.forEach { (key, value) ->
                    if (value == null) remove(key) else putString(key, value)
                } }.commit())
            }
        }
    }

    @Test fun qwertyLanguageShortcutDoesNotStealAnExistingCustomBinding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val preferences = ImePreferences(instrumentation.targetContext)
            val store = preferences.store
            val keys = listOf("shortcut_language", "shortcut_history", "shortcut_transform", "shortcut_qwerty_language")
            val saved = keys.associateWith { store.getString(it, null) }
            try {
                check(store.edit().apply { keys.forEach { remove(it) } }.commit())
                val default = ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.FN or ShortcutBinding.SHIFT)
                assertEquals(default, preferences.shortcut("qwerty_language"))
                preferences.setShortcut("history", default)
                assertNull(preferences.shortcut("qwerty_language"))
                assertEquals(default, preferences.shortcuts().history)
                val custom = ShortcutBinding(KeyEvent.KEYCODE_J, ShortcutBinding.CTRL or ShortcutBinding.SHIFT)
                preferences.setShortcut("qwerty_language", custom)
                assertEquals(custom, preferences.shortcuts().qwertyLanguage)
                assertEquals(custom, ImePreferences(instrumentation.targetContext).shortcuts().qwertyLanguage)
                assertEquals("qwerty_language", preferences.shortcuts().ownerOf(custom, "language"))
            } finally {
                check(store.edit().apply { saved.forEach { (key, value) ->
                    if (value == null) remove(key) else putString(key, value)
                } }.commit())
            }
        }
    }
}
