package dev.utmity.titanime

import android.content.Context
import android.view.KeyEvent

internal class ImePreferences(context: Context) {
    val store = context.getSharedPreferences("ime_settings", Context.MODE_PRIVATE)
    var voiceLanguage: String
        get() = store.getString("voice_language", "auto") ?: "auto"
        set(value) { store.edit().putString("voice_language", value).apply() }
    var transformKey: String
        get() = store.getString("transform_key", "t") ?: "t"
        set(value) { store.edit().putString("transform_key", value).apply() }
    var editing: Boolean
        get() = store.getBoolean("editing", true)
        set(value) { store.edit().putBoolean("editing", value).apply() }
    var clipboardKey: String
        get() = store.getString("clipboard_key", "v") ?: "v"
        set(value) { store.edit().putString("clipboard_key", value).apply() }
    var editingLayout: String
        get() = store.getString("editing_layout", "hjkl") ?: "hjkl"
        set(value) { store.edit().putString("editing_layout", value).apply() }
    var wordDelete: Boolean
        get() = store.getBoolean("word_delete", false)
        set(value) { store.edit().putBoolean("word_delete", value).apply() }
    var altText: Boolean
        get() = store.getBoolean("alt_text", true)
        set(value) { store.edit().putBoolean("alt_text", value).apply() }
    var prediction: Boolean
        get() = store.getBoolean("prediction", true)
        set(value) { store.edit().putBoolean("prediction", value).apply() }
    var correction: Boolean
        get() = store.getBoolean("correction", true)
        set(value) { store.edit().putBoolean("correction", value).apply() }
    var learning: Boolean
        get() = store.getBoolean("learning", true)
        set(value) { store.edit().putBoolean("learning", value).apply() }
    var toggle: String
        get() = store.getString("toggle", "fn") ?: "fn"
        set(value) { store.edit().putString("toggle", value).apply() }
    var segments: String
        get() = store.getString("segments", "hl") ?: "hl"
        set(value) { store.edit().putString("segments", value).apply() }
    var japanese: Boolean
        get() = store.getBoolean("japanese", false)
        set(value) { store.edit().putBoolean("japanese", value).apply() }
    var toolbarMode: String
        get() = store.getString("toolbar_mode", "always") ?: "always"
        set(value) {
            require(value in setOf("always", "compose", "conversion"))
            store.edit().putString("toolbar_mode", value).apply()
        }

    fun shortcut(action: String): ShortcutBinding? {
        require(action in setOf("language", "history", "transform"))
        val saved = store.getString("shortcut_$action", null)
        if (saved == "off") return null
        if (saved != null) return ShortcutBinding.decode(saved)
        // Preserve existing profiles until the user explicitly records a new key.
        return when (action) {
            "language" -> when (toggle) {
                "sym" -> ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.SYM)
                "alt" -> ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.ALT)
                else -> ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.FN)
            }
            "history" -> when (clipboardKey) {
                "off" -> null
                "b" -> ShortcutBinding(KeyEvent.KEYCODE_B, ShortcutBinding.FN)
                else -> ShortcutBinding(KeyEvent.KEYCODE_V, ShortcutBinding.FN)
            }
            else -> when (transformKey) {
                "off" -> null
                "g" -> ShortcutBinding(KeyEvent.KEYCODE_G, ShortcutBinding.FN)
                else -> ShortcutBinding(KeyEvent.KEYCODE_T, ShortcutBinding.FN)
            }
        }
    }

    fun setShortcut(action: String, binding: ShortcutBinding?) {
        require(action in setOf("language", "history", "transform"))
        require(binding == null || !binding.reserved())
        store.edit().putString("shortcut_$action", binding?.encode() ?: "off").apply()
    }

    fun shortcuts() = ShortcutProfile(shortcut("language"), shortcut("history"), shortcut("transform"))

    fun matchesToggle(event: KeyEvent, fn: Boolean): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_SPACE || event.isShiftPressed || event.isCtrlPressed || event.isMetaPressed) return false
        return when (toggle) {
            "sym" -> event.isSymPressed && !fn && !event.isAltPressed
            "alt" -> event.isAltPressed && !fn && !event.isSymPressed
            else -> fn && !event.isAltPressed && !event.isSymPressed
        }
    }
    fun segmentBindings(): SegmentBindings {
        if (segments != "jk") return SegmentBindings()
        return SegmentBindings(listOf(
            SegmentBinding(KeyEvent.KEYCODE_J, false, SegmentAction.PREVIOUS),
            SegmentBinding(KeyEvent.KEYCODE_K, false, SegmentAction.NEXT),
            SegmentBinding(KeyEvent.KEYCODE_U, false, SegmentAction.SHRINK),
            SegmentBinding(KeyEvent.KEYCODE_O, false, SegmentAction.EXPAND)))
    }
}
