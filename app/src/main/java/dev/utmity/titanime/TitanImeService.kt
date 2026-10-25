package dev.utmity.titanime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent as MozcKey

/** Physical routing belongs here; Japanese composition and conversion belong to Mozc. */
class TitanImeService : InputMethodService() {
    private val preferences by lazy { ImePreferences(this) }
    private var engine: MozcEngine? = null
    private var japanese = false
    private var directField = false
    private var compositionStart = -1
    private var preedit = ""
    private var output = Output.getDefaultInstance()
    private var candidateRow: LinearLayout? = null
    private val titanFnDevices = mutableSetOf<Int>()
    private val consumedKeys = mutableSetOf<Pair<Int, Int>>()

    override fun onCreate() {
        super.onCreate()
        japanese = preferences.japanese
        try { engine = MozcEngine(this) }
        catch (error: Exception) { android.util.Log.e("TitanImeProbe", "Mozc initialization failed", error) }
        catch (error: LinkageError) { android.util.Log.e("TitanImeProbe", "Mozc native library unavailable", error) }
    }

    private fun isTitanFn(event: KeyEvent) = event.keyCode == 403 && event.scanCode == 251

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isTitanFn(event)) titanFnDevices.remove(event.deviceId)
        if (consumedKeys.remove(event.deviceId to keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onFinishInput() {
        engine?.close()
        preedit = ""
        compositionStart = -1
        output = Output.getDefaultInstance()
        titanFnDevices.clear()
        consumedKeys.clear()
        // Do not enqueue a hide here: it can arrive after the next editor starts
        // and finish that editor's new composing span. The system owns teardown.
        refreshStrip(updateVisibility = false)
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return japanese && !directField
    }

    // A compact input view is used instead of the candidates-only window, which
    // this device clips to the navigation bar when no software input view exists.
    override fun onCreateInputView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(android.graphics.Color.WHITE)
        // Some hardware-keyboard devices report zero bottom inset for the IME
        // even while its navigation buttons overlap the candidate window.
        val navigationClearance = (32 * resources.displayMetrics.density).toInt()
        val scroll = HorizontalScrollView(this@TitanImeService).apply {
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            candidateRow = LinearLayout(this@TitanImeService).also { addView(it) }
        }
        addView(scroll, LinearLayout.LayoutParams(-1, -2))
        val spacer = View(this@TitanImeService)
        addView(spacer, LinearLayout.LayoutParams(-1, navigationClearance))
        setOnApplyWindowInsetsListener { _, insets ->
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30)
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            else insets.systemWindowInsetBottom
            val height = maxOf(bottom, navigationClearance)
            if (spacer.layoutParams.height != height) {
                spacer.layoutParams = LinearLayout.LayoutParams(-1, height)
            }
            insets
        }
        refreshStrip(updateVisibility = false)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        engine?.start()
        preedit = ""
        compositionStart = -1
        output = Output.getDefaultInstance()
        titanFnDevices.clear()
        consumedKeys.clear()
        val type = attribute?.inputType ?: 0
        val variation = type and InputType.TYPE_MASK_VARIATION
        directField = (type and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT ||
            variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        refreshStrip()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            attribute?.packageName == "dev.utmity.titanime.harness") {
            sendBroadcast(android.content.Intent("dev.utmity.titanime.INPUT_READY")
                .setPackage("dev.utmity.titanime.harness").putExtra("japanese", japanese))
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            currentInputEditorInfo?.packageName == "dev.utmity.titanime.harness") {
            android.util.Log.i("TitanImeProbe", "selection=$oldSelStart,$oldSelEnd->$newSelStart,$newSelEnd composing=$candidatesStart,$candidatesEnd length=${preedit.length}")
        }
        if (preedit.isEmpty()) return
        val ic = currentInputConnection ?: return
        if (candidatesStart >= 0 && newSelStart == newSelEnd &&
            newSelStart in candidatesStart..candidatesEnd) {
            // Android reports UTF-16 offsets; Mozc uses Unicode character positions.
            // Ignore obsolete range notifications from an earlier batched update.
            if (candidatesEnd - candidatesStart != preedit.length) return
            compositionStart = candidatesStart
            val offset = newSelStart - candidatesStart
            val expected = preedit.offsetByCodePoints(0, output.preedit.cursor.coerceIn(0, preedit.codePointCount(0, preedit.length)))
            if (offset == expected) return
            if (!output.preedit.hasHighlightedPosition()) {
                engine?.moveCursor(preedit.codePointCount(0, offset))?.let { applyOutput(ic, it) }
            } else {
                // MOVE_CURSOR is composition-only upstream. Preserve conversion and
                // select the segment touched, rather than resetting the session.
                val segments = output.preedit.segmentList
                var end = 0
                val target = segments.indexOfFirst { end += it.value.length; offset < end }
                    .let { if (it < 0) segments.lastIndex else it }
                val focused = segments.indexOfFirst { it.annotation == Preedit.Segment.Annotation.HIGHLIGHT }
                if (focused >= 0 && target >= 0) {
                    repeat(kotlin.math.abs(target - focused)) {
                        moveSegment(if (target < focused) MozcKey.SpecialKey.LEFT else MozcKey.SpecialKey.RIGHT)
                    }
                }
                val caret = preedit.offsetByCodePoints(0, output.preedit.cursor.coerceIn(0, preedit.codePointCount(0, preedit.length)))
                ic.setSelection(compositionStart + caret, compositionStart + caret)
            }
            return
        }
        // Outside the composing range, or a non-collapsed selection: preserve the
        // visible text and the editor's selection, then discard only the session state.
        ic.finishComposingText()
        engine?.start()
        preedit = ""
        compositionStart = -1
        output = Output.getDefaultInstance()
        refreshStrip()
    }

    private fun moveSegment(key: MozcKey.SpecialKey, shift: Boolean = false) {
        val ic = currentInputConnection ?: return
        if (!output.preedit.hasHighlightedPosition()) return
        val event = MozcKey.newBuilder().setSpecialKey(key)
        if (shift) event.addModifierKeys(MozcKey.ModifierKey.SHIFT)
        engine?.key(event.build())?.let { applyOutput(ic, it) }
    }

    private fun refreshStrip(updateVisibility: Boolean = true) {
        candidateRow?.apply {
            removeAllViews()
            addView(TextView(this@TitanImeService).apply {
                text = if (engine == null) "Mozc unavailable" else "あ  "
                setTextColor(android.graphics.Color.DKGRAY)
                setPadding(12, 12, 12, 12)
            })
            val window = output.candidateWindow
            if (output.preedit.hasHighlightedPosition()) {
                addView(TextView(this@TitanImeService).apply {
                    text = "読みに戻す"
                    setTextColor(android.graphics.Color.BLUE)
                    setPadding(18, 12, 18, 12)
                    setOnClickListener {
                        val ic = currentInputConnection ?: return@setOnClickListener
                        engine?.key(MozcKey.newBuilder().setSpecialKey(MozcKey.SpecialKey.ESCAPE).build())
                            ?.let { applyOutput(ic, it) }
                    }
                })
                for ((label, key) in listOf("前文節" to MozcKey.SpecialKey.LEFT, "次文節" to MozcKey.SpecialKey.RIGHT)) {
                    addView(TextView(this@TitanImeService).apply {
                        text = label
                        setTextColor(android.graphics.Color.BLUE)
                        setPadding(18, 12, 18, 12)
                        setOnClickListener { moveSegment(key) }
                    })
                }
            }
            if (window.candidateCount == 0) addView(TextView(this@TitanImeService).apply {
                text = preedit
                textSize = 18f
                setTextColor(android.graphics.Color.BLACK)
                setPadding(18, 12, 18, 12)
            })
            for (candidate in window.candidateList) {
                addView(TextView(this@TitanImeService).apply {
                    text = (if (window.hasFocusedIndex() && window.focusedIndex == candidate.index) "● " else "") + candidate.value
                    textSize = 18f
                    setTextColor(android.graphics.Color.BLACK)
                    setPadding(18, 12, 18, 12)
                    setOnClickListener {
                        val ic = currentInputConnection ?: return@setOnClickListener
                        val result = engine?.command(SessionCommand.CommandType.HIGHLIGHT_CANDIDATE, candidate.id)
                            ?: return@setOnClickListener
                        applyOutput(ic, result)
                    }
                })
            }
        }
        if (updateVisibility) {
            if (japanese && !directField) {
                if (!isInputViewShown) requestShowSelf(0)
            } else if (isInputViewShown) requestHideSelf(0)
        }
    }

    private fun applyOutput(ic: InputConnection, result: Output) {
        ic.beginBatchEdit()
        try {
            if (result.hasResult()) {
                ic.commitText(result.result.value, 1)
                compositionStart = -1
            }
            if (compositionStart < 0) {
                ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.let {
                    compositionStart = it.startOffset + minOf(it.selectionStart, it.selectionEnd)
                }
            }
            val next = result.preedit.segmentList.joinToString("") { it.value }
            if (next.isNotEmpty()) {
                val styled = android.text.SpannableString(next)
                var offset = 0
                for (segment in result.preedit.segmentList) {
                    if (segment.annotation == Preedit.Segment.Annotation.HIGHLIGHT) {
                        styled.setSpan(android.text.style.BackgroundColorSpan(0xFFBBDEFB.toInt()),
                            offset, offset + segment.value.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    offset += segment.value.length
                }
                ic.setComposingText(styled, 1)
                if (compositionStart >= 0) {
                    val cursor = next.offsetByCodePoints(0, result.preedit.cursor.coerceIn(0, next.codePointCount(0, next.length)))
                    ic.setSelection(compositionStart + cursor, compositionStart + cursor)
                }
            }
            else {
                if (preedit.isNotEmpty() && !result.hasResult()) ic.setComposingText("", 1)
                ic.finishComposingText()
                compositionStart = -1
            }
            preedit = next
            output = result
        } finally { ic.endBatchEdit() }
        refreshStrip()
    }

    private fun commitComposition(ic: InputConnection) {
        if (preedit.isEmpty()) return
        // Ask Mozc to resolve pending romaji (notably final n) before kana commitment.
        // An already converted candidate must retain the user's selected spelling.
        if (!output.preedit.hasHighlightedPosition()) {
            engine?.key(MozcKey.newBuilder().setSpecialKey(MozcKey.SpecialKey.F6).build())
        }
        engine?.command(SessionCommand.CommandType.SUBMIT)?.let { applyOutput(ic, it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val consumed = handleKey(keyCode, event)
        if (consumed) consumedKeys.add(event.deviceId to keyCode)
        return consumed
    }

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (isTitanFn(event)) titanFnDevices.add(event.deviceId)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            currentInputEditorInfo?.packageName == "dev.utmity.titanime.harness") {
            android.util.Log.i("TitanImeProbe", "key=$keyCode meta=${event.metaState} japanese=$japanese connected=${currentInputConnection != null}")
        }
        val ic = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        if (preferences.matchesToggle(event, event.isFunctionPressed || event.deviceId in titanFnDevices)) {
            if (event.repeatCount == 0 && engine != null && !directField) {
                if (japanese) commitComposition(ic)
                japanese = !japanese
                preferences.japanese = japanese
                refreshStrip()
            }
            return true
        }
        if (japanese && !directField && output.preedit.hasHighlightedPosition()) {
            // Modifier down must not commit conversion before its chord arrives.
            if (isTitanFn(event) || keyCode == KeyEvent.KEYCODE_FUNCTION ||
                keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) return true
            val action = preferences.segmentBindings().resolve(event, event.isFunctionPressed || event.deviceId in titanFnDevices)
            if (action != null) {
                moveSegment(if (action == SegmentAction.PREVIOUS || action == SegmentAction.SHRINK)
                    MozcKey.SpecialKey.LEFT else MozcKey.SpecialKey.RIGHT,
                    action == SegmentAction.SHRINK || action == SegmentAction.EXPAND)
                return true
            }
        }
        if (japanese && !directField && engine != null) {
            // Wait for the actual chord key; a modifier alone has no text to commit.
            if (preedit.isNotEmpty() && (isTitanFn(event) || keyCode == KeyEvent.KEYCODE_SYM ||
                keyCode == KeyEvent.KEYCODE_FUNCTION || keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                keyCode == KeyEvent.KEYCODE_ALT_RIGHT || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)) return true
            val character = event.unicodeChar
            if (!event.isCtrlPressed && !event.isMetaPressed &&
                character in 33..126 && !character.toChar().isLetterOrDigit()) {
                // Use the character actually delivered by Android; never invent a
                // symbol mapping for an Fn/Sym letter whose character is still a letter.
                val result = engine!!.key(MozcKey.newBuilder().setKeyCode(character).build())
                applyOutput(ic, result)
                return result.consumed
            }
        }
        if (event.isSymPressed || event.isFunctionPressed || event.deviceId in titanFnDevices) {
            if (japanese && preedit.isNotEmpty()) {
                commitComposition(ic)
                ic.sendKeyEvent(event)
                ic.sendKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        if (!japanese || directField || engine == null) return super.onKeyDown(keyCode, event)
        if (event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) {
            commitComposition(ic)
            ic.sendKeyEvent(event)
            ic.sendKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (output.preedit.hasHighlightedPosition()) {
                moveSegment(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) MozcKey.SpecialKey.LEFT else MozcKey.SpecialKey.RIGHT,
                    event.isShiftPressed)
                return true
            }
            if (preedit.isNotEmpty() && !event.isShiftPressed) {
                val result = engine!!.key(MozcKey.newBuilder().setSpecialKey(
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) MozcKey.SpecialKey.LEFT else MozcKey.SpecialKey.RIGHT).build())
                applyOutput(ic, result)
                return result.consumed
            }
        }
        val special = when (keyCode) {
            KeyEvent.KEYCODE_DEL -> MozcKey.SpecialKey.BACKSPACE
            KeyEvent.KEYCODE_SPACE -> MozcKey.SpecialKey.SPACE
            KeyEvent.KEYCODE_ENTER -> MozcKey.SpecialKey.ENTER
            KeyEvent.KEYCODE_ESCAPE -> MozcKey.SpecialKey.ESCAPE
            KeyEvent.KEYCODE_DPAD_DOWN -> MozcKey.SpecialKey.DOWN
            KeyEvent.KEYCODE_DPAD_UP -> MozcKey.SpecialKey.UP
            else -> null
        }
        if (special != null) {
            if (preedit.isEmpty()) return super.onKeyDown(keyCode, event)
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                commitComposition(ic)
                return true
            }
            val key = MozcKey.newBuilder().setSpecialKey(special)
            if (event.isShiftPressed) key.addModifierKeys(MozcKey.ModifierKey.SHIFT)
            val result = engine!!.key(key.build())
            applyOutput(ic, result)
            return result.consumed
        }
        val unicode = event.unicodeChar
        if (unicode in 1..127) {
            val c = unicode.toChar()
            if (!c.isDigit()) {
                val result = engine!!.key(MozcKey.newBuilder().setKeyCode(c.lowercaseChar().code).build())
                applyOutput(ic, result)
                return result.consumed
            }
            if (preedit.isNotEmpty()) {
                commitComposition(ic)
                ic.sendKeyEvent(event)
                ic.sendKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
