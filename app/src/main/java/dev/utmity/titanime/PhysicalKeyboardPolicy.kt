package dev.utmity.titanime

import android.view.InputDevice
import android.view.KeyEvent

/** Interpret only the observed built-in keyboard's actual KCM, never a copied letter table. */
internal object PhysicalKeyboardPolicy {
    fun isTitanKeyboard(device: InputDevice?) = device != null && device.name == "TitanKey" &&
        device.vendorId == 0x2533 && device.productId == 0x2533

    fun altText(event: KeyEvent, fnHeld: Boolean, textMode: Boolean): Int? {
        if (!textMode || !isTitanKeyboard(event.device) || !event.isAltPressed ||
            event.isCtrlPressed || event.isMetaPressed || event.isSymPressed || fnHeld) return null
        // Only the verified alphabet-key symbol/number layer. Alt+Space is an IME
        // or system shortcut; its vendor private-use character is not text.
        if (event.keyCode !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) return null
        val value = event.unicodeChar
        val base = event.getUnicodeChar(event.metaState and KeyEvent.META_ALT_MASK.inv())
        return value.takeIf { it in 33..126 && !it.toChar().isLetter() && it != base }
    }
}
