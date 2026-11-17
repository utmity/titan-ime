package dev.utmity.titanime

/** Ephemeral panel state. No clipboard access or persistence in presentation code. */
internal sealed interface CurrentClipboard {
    data class Text(val value: String, val sensitive: Boolean) : CurrentClipboard
    data object Empty : CurrentClipboard
    data object Unsupported : CurrentClipboard
    data object Unavailable : CurrentClipboard
}

internal object ClipboardPanel {
    data class Saved(val text: String, val pinned: Boolean)
    data class Row(val text: String, val current: Boolean, val sensitive: Boolean,
        val stored: Boolean, val pinned: Boolean) {
        fun label(): String {
            val prefix = (if (current) "現在のクリップボード · " else "") + (if (pinned) "★ " else "")
            if (sensitive) return prefix + "機密情報（内容を非表示・タップで貼り付け）"
            val preview = ClipboardPreview.of(text)
            return prefix + (if (preview.lines > 1) "[${preview.lines}行] " else "") + preview.text
        }
    }

    fun rows(current: CurrentClipboard, saved: List<Saved>): List<Row> {
        val clip = current as? CurrentClipboard.Text
        val first = clip?.let {
            Row(it.value, true, it.sensitive, saved.any { entry -> entry.text == it.value },
                saved.firstOrNull { entry -> entry.text == it.value }?.pinned == true)
        }
        return listOfNotNull(first) + saved.distinctBy { it.text }.filter { it.text != clip?.value }
            .map { Row(it.text, false, false, true, it.pinned) }
    }
}

/** Bounded display only: CRLF is one newline; LF, CR and trailing empty lines count. */
internal object ClipboardPreview {
    data class Preview(val text: String, val lines: Int)
    fun of(raw: String, maxCodePoints: Int = 120): Preview {
        require(maxCodePoints > 0)
        val display = StringBuilder()
        var lines = 1
        var index = 0
        var shown = 0
        var omitted = false
        while (index < raw.length) {
            val codePoint = raw.codePointAt(index)
            index += Character.charCount(codePoint)
            val newline = codePoint == '\r'.code || codePoint == '\n'.code
            if (codePoint == '\r'.code && index < raw.length && raw[index] == '\n') index++
            if (newline) lines++
            if (shown < maxCodePoints) {
                if (newline) display.append(" ↵ ") else display.appendCodePoint(codePoint)
                shown++
            } else omitted = true
        }
        if (omitted) display.append('…')
        return Preview(display.toString(), lines)
    }
}
