package dev.utmity.titanime

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/**
 * ImePreferences serves the input hot path from an in-memory snapshot. The
 * snapshot must stay indistinguishable from the store: direct store writes
 * (the settings UI uses its own instance; some flows edit the store) have to
 * show up in cached getters, and setters must persist as before.
 */
class ImePreferencesCacheTest {
    @Test fun cachedValuesTrackStoreAndSetters() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ImePreferences(context)
        val store = preferences.store
        val originalWordDelete = store.getBoolean("word_delete", false)
        val originalLayout = store.getString("editing_layout", "hjkl")
        val originalSegments = store.getString("segments", "hl")
        val originalShortcutHistory = store.getString("shortcut_history", null)
        try {
            // Prime the cache, then write the store directly: the getter must
            // observe the new value without any setter being involved.
            assertEquals(originalWordDelete, preferences.wordDelete)
            check(store.edit().putBoolean("word_delete", !originalWordDelete).commit())
            assertEquals(!originalWordDelete, preferences.wordDelete)
            check(store.edit().putString("editing_layout", "wasd").commit())
            assertEquals("wasd", preferences.editingLayout)

            // Setters update both the cache and the persisted store.
            preferences.editingLayout = "hjkl"
            assertEquals("hjkl", store.getString("editing_layout", null))
            assertEquals("hjkl", preferences.editingLayout)

            // Derived segment bindings are cached while the setting is stable
            // and rebuilt when it changes.
            val defaults = preferences.segmentBindings()
            assertSame(defaults, preferences.segmentBindings())
            check(store.edit().putString("segments", "jk").commit())
            assertNotSame(defaults, preferences.segmentBindings())
            assertSame(preferences.segmentBindings(), preferences.segmentBindings())

            // Shortcut round-trip through the cached profile path.
            preferences.setShortcut("history", null)
            assertNull(preferences.shortcut("history"))
            assertEquals("off", store.getString("shortcut_history", null))
        } finally {
            val editor = store.edit()
                .putBoolean("word_delete", originalWordDelete)
                .putString("editing_layout", originalLayout)
                .putString("segments", originalSegments)
            if (originalShortcutHistory == null) editor.remove("shortcut_history")
            else editor.putString("shortcut_history", originalShortcutHistory)
            check(editor.commit())
        }
    }

    /**
     * The service reacts to settings changes only through changeListener.
     * That callback must run after the snapshot absorbed the change —
     * including for writes made directly to the store, as the settings UI's
     * own instance produces — so a consumer can never observe a stale value,
     * regardless of store-listener invocation order.
     */
    @Test fun changeListenerObservesUpdatedSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ImePreferences(context)
        val original = preferences.store.getBoolean("word_delete", false)
        val observed = mutableListOf<Boolean>()
        preferences.changeListener = { key ->
            if (key == "word_delete") observed += preferences.wordDelete
        }
        try {
            check(preferences.store.edit().putBoolean("word_delete", !original).commit())
            assertEquals(listOf(!original), observed)
            preferences.wordDelete = original
            assertEquals(listOf(!original, original), observed)
        } finally {
            preferences.changeListener = null
            check(preferences.store.edit().putBoolean("word_delete", original).commit())
        }
    }
}
