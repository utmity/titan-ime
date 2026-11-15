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
    enum class Hand { LEFT, RIGHT }

    sealed interface Action {
        data class Character(val value: Char) : Action
        data object MoveLeft : Action
        data object MoveRight : Action
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

    fun translate(keyCode: Int, hand: Hand): Action? {
        val rows = if (hand == Hand.LEFT) leftRows else rightRows
        val index = rows.indexOf(keyCode)
        if (index >= 0) return Action.Character('1' + index)
        return when (keyCode) {
            KeyEvent.KEYCODE_G -> Action.Character('0') // わ / を / ん / ー (stock table also has ゎ)
            KeyEvent.KEYCODE_T -> Action.Character('*') // dakuten, handakuten, small kana
            KeyEvent.KEYCODE_V -> Action.Character('#') // punctuation (stock table lacks …)
            if (hand == Hand.LEFT) KeyEvent.KEYCODE_Q else KeyEvent.KEYCODE_O -> Action.MoveLeft
            if (hand == Hand.LEFT) KeyEvent.KEYCODE_A else KeyEvent.KEYCODE_L -> Action.MoveRight
            else -> null
        }
    }
}
