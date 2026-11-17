package dev.utmity.titanime

import android.content.ClipData
import android.content.ClipboardManager

/** Use only for an active eligible IME or an explicit panel action. Never coerce URI/Intent items. */
internal object ClipboardTextReader {
    fun read(clipboard: ClipboardManager): CurrentClipboard = try {
        fromClip(clipboard.primaryClip)
    } catch (_: SecurityException) {
        CurrentClipboard.Unavailable
    } catch (_: IllegalStateException) {
        CurrentClipboard.Unavailable
    }

    fun fromClip(clip: ClipData?): CurrentClipboard {
        if (clip == null || clip.itemCount == 0) return CurrentClipboard.Empty
        if (clip.itemCount != 1) return CurrentClipboard.Unsupported
        val item = clip.getItemAt(0)
        if (item.uri != null || item.intent != null || item.text == null) return CurrentClipboard.Unsupported
        val text = item.text.toString()
        if (text.isEmpty()) return CurrentClipboard.Empty
        val sensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        return CurrentClipboard.Text(text, sensitive)
    }
}
