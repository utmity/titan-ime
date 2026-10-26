package dev.utmity.titanime

import android.content.Context
import android.view.KeyEvent

internal class ImePreferences(context: Context) {
    val store = context.getSharedPreferences("ime_settings", Context.MODE_PRIVATE)
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
