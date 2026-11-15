package dev.utmity.titanime

import android.view.KeyEvent

/**
 * Physical-key translation for the separate Preview application only.
 * TitanImeService keeps the production QWERTY route unchanged.
 *
 * The returned characters are the stock Mozc twelve-key table inputs
 * (1..9, 0, *, #), not characters to commit to the editor.
 */
object TwelveKeyKeymap {
    sealed interface Action {
        data object NextMode : Action
        data class Character(val value: Char) : Action
        data class Navigate(val keyCode: Int) : Action
    }

    private val leftRows = intArrayOf(
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
        KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F,
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C,
    )
    private val rightRows = intArrayOf(
        KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
        KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K,
        KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M,
    )

    fun translate(keyCode: Int, swapCursorAxes: Boolean = false): Action? {
        // Both physical halves feed the same Mozc tokens in every 12-key mode.
        val index = leftRows.indexOf(keyCode).takeIf { it >= 0 } ?: rightRows.indexOf(keyCode)
        if (index >= 0) return Action.Character('1' + index)
        return when (keyCode) {
            KeyEvent.KEYCODE_P -> Action.NextMode
            KeyEvent.KEYCODE_G -> Action.Character('0')
            KeyEvent.KEYCODE_T -> Action.Character('*')
            KeyEvent.KEYCODE_V -> Action.Character('#')
            KeyEvent.KEYCODE_Q -> Action.Navigate(if (swapCursorAxes) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_LEFT)
            KeyEvent.KEYCODE_A -> Action.Navigate(if (swapCursorAxes) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyEvent.KEYCODE_O -> Action.Navigate(if (swapCursorAxes) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_UP)
            KeyEvent.KEYCODE_L -> Action.Navigate(if (swapCursorAxes) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN)
            else -> null
        }
    }
}
