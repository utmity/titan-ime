package dev.utmity.titanime

import android.view.KeyEvent

/** Data-only bindings, separate from routing selected by the settings profile. */
internal data class SegmentBinding(val keyCode: Int, val shift: Boolean, val action: SegmentAction)
internal enum class SegmentAction { PREVIOUS, NEXT, SHRINK, EXPAND }
internal class SegmentBindings(private val bindings: List<SegmentBinding> = defaults) {
    fun resolve(event: KeyEvent, fnHeld: Boolean): SegmentAction? {
        if (!fnHeld || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed || event.isSymPressed) return null
        return bindings.firstOrNull { it.keyCode == event.keyCode && it.shift == event.isShiftPressed }?.action
    }
    companion object {
        val defaults = listOf(
            SegmentBinding(KeyEvent.KEYCODE_H, false, SegmentAction.PREVIOUS),
            SegmentBinding(KeyEvent.KEYCODE_L, false, SegmentAction.NEXT),
            SegmentBinding(KeyEvent.KEYCODE_U, false, SegmentAction.SHRINK),
            SegmentBinding(KeyEvent.KEYCODE_O, false, SegmentAction.EXPAND))
    }
}
