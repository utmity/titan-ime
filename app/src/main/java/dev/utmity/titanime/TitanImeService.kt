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
    private var selectedReading: Pair<Int, Int>? = null
    private var compositionStart = -1
    private var preedit = ""
    private var output = Output.getDefaultInstance()
    private var candidateRow: LinearLayout? = null
    private var menuButton: TextView? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private var supportPanel: LinearLayout? = null
    private val voice by lazy { VoiceInput(this) }
    private var voiceStatus = ""
    private var inlineVisible = false
    private var transformIndex = 0
    private var panel = ""
    private var historyQuery = ""
    private var historyIndex = 0
    private var inlineBatch: InlineBatch<View>? = null
    private var autofillRow: LinearLayout? = null
    private val history by lazy { ClipboardHistory(this) }
    private val clipboard by lazy { getSystemService(android.content.ClipboardManager::class.java) }
    private val clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        if (currentInputStarted && !directField && currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0 && history.enabled) {
            val clip = clipboard.primaryClip
            val sensitive = clip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            if (clip != null && clip.itemCount == 1) clip.getItemAt(0).text?.let { history.capture(it.toString(), sensitive) }
            if (panel == "history") renderPanel()
        }
    }
    private val titanFnDevices = mutableSetOf<Int>()
    // True only if every Down in this physical press was consumed. If any
    // repeat reached the app, the matching Up must also reach it.
    private val consumedKeys = mutableMapOf<Pair<Int, Int>, Boolean>()
    private var traceRevision = 0L
    private var writingBatch = false
    private var inputGeneration = 0L
    private var imeWindowVisible = false
    private var lastCompositionWriteAccepted = false
    private var initialKeyHandoff: InitialKeyHandoff? = null
    private fun clearPressedKeys() {
        val carried = initialKeyHandoff?.takeIf { it.live(android.os.SystemClock.uptimeMillis()) }
            ?.physicalKey?.let { key -> consumedKeys[key]?.let { key to it } }
        consumedKeys.clear()
        carried?.let { consumedKeys[it.first] = it.second }
    }
    private fun traceLifecycle(event: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            android.util.Log.isLoggable("TitanLifecycle", android.util.Log.DEBUG)) {
            android.util.Log.d("TitanLifecycle", "generation=$inputGeneration started=$currentInputStarted connected=${currentInputConnection != null} direct=$directField view=$isInputViewShown decor=${window?.window?.decorView?.isShown} preeditLength=${preedit.length} $event")
        }
    }
    private fun traceState(event: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            !directField && android.util.Log.isLoggable("TitanState", android.util.Log.DEBUG)) {
            android.util.Log.d("TitanState", "rev=$traceRevision batch=$writingBatch preedit=${preedit.length} start=$compositionStart conversion=${output.preedit.hasHighlightedPosition()} $event")
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboard.addPrimaryClipChangedListener(clipListener)
        japanese = preferences.japanese
        try { engine = MozcEngine(this) }
        catch (error: Exception) { android.util.Log.e("TitanImeProbe", "Mozc initialization failed", error) }
        catch (error: LinkageError) { android.util.Log.e("TitanImeProbe", "Mozc native library unavailable", error) }
    }

    private fun isTitanFn(event: KeyEvent) = event.keyCode == 403 && event.scanCode == 251

    override fun onDestroy() {
        voice.cancel()
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    @android.annotation.TargetApi(30)
    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        // Remote provider views are independent of direct key routing. No secret
        // is read through InputConnection or copied into the IME's own data model.
        if (!androidx.autofill.inline.UiVersions.getVersions(uiExtras).contains(androidx.autofill.inline.UiVersions.INLINE_UI_VERSION_1)) return null
        val height = (48 * resources.displayMetrics.density).toInt()
        val styles = androidx.autofill.inline.UiVersions.newStylesBuilder()
            .addStyle(androidx.autofill.inline.v1.InlineSuggestionUi.newStyleBuilder().build()).build()
        val spec = android.widget.inline.InlinePresentationSpec.Builder(android.util.Size(0, height),
            android.util.Size(resources.displayMetrics.widthPixels, height)).setStyle(styles).build()
        return android.view.inputmethod.InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(3).build()
    }

    @android.annotation.TargetApi(30)
    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        inlineBatch?.cancel()
        inlineBatch = null
        autofillRow?.removeAllViews()
        inlineVisible = false
        if (response.inlineSuggestions.isEmpty()) { refreshStrip(); return false }
        if (autofillRow == null) setInputView(onCreateInputView())
        val row = autofillRow ?: return false
        val suggestions = response.inlineSuggestions.take(3)
        val batch = InlineBatch<View>(suggestions.size)
        inlineBatch = batch
        fun accept(index: Int, content: View?) {
            if (inlineBatch !== batch || autofillRow !== row || !currentInputStarted || !batch.complete(index, content)) return
            // Insert newly completed views in provider order without detaching
            // already-live remote surfaces.
            batch.ordered().forEachIndexed { position, view ->
                if (view.parent == null) row.addView(view, position)
            }
            inlineVisible = row.childCount > 0
            if (inlineVisible) {
                requestShowSelf(0)
                refreshStrip(updateVisibility = false)
            } else if (batch.finished()) refreshStrip()
        }
        suggestions.forEachIndexed { index, suggestion ->
            val size = suggestion.info.inlinePresentationSpec.minSize
            try {
                suggestion.inflate(this, android.util.Size(-2, size.height), mainExecutor) { content -> accept(index, content) }
            } catch (_: RuntimeException) {
                // A failed provider presentation must not crash physical input.
                accept(index, null)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        traceLifecycle("keyUp press=${event.downTime} repeat=${event.repeatCount}")
        if (isTitanFn(event)) titanFnDevices.remove(event.deviceId)
        if (consumedKeys.remove(event.deviceId to keyCode) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onFinishInput() {
        traceLifecycle("finishInput")
        voice.cancel(); inlineVisible = false
        panel = ""; inlineBatch?.cancel(); inlineBatch = null; supportPanel?.removeAllViews(); autofillRow?.removeAllViews()
        traceState("finishInput")
        engine?.close()
        engine?.sync()
        preedit = ""
        compositionStart = -1
        selectedReading = null
        output = Output.getDefaultInstance()
        titanFnDevices.clear()
        clearPressedKeys()
        // Do not enqueue a hide here: it can arrive after the next editor starts
        // and finish that editor's new composing span. The system owns teardown.
        refreshStrip(updateVisibility = false)
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode() = false
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        traceLifecycle("startInputView restarting=$restarting")
        super.onStartInputView(info, restarting)
        refreshStrip(updateVisibility = false)
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        traceLifecycle("finishInputView finishing=$finishingInput")
        super.onFinishInputView(finishingInput)
    }
    override fun onWindowShown() {
        imeWindowVisible = true
        super.onWindowShown()
        traceLifecycle("windowShown")
    }
    override fun onWindowHidden() {
        if (voice.active) voiceStatus = "画面が閉じられたため中止しました"
        voice.cancel()
        imeWindowVisible = false
        inlineBatch?.cancel(); inlineBatch = null; inlineVisible = false
        autofillRow?.removeAllViews()
        refreshStrip(updateVisibility = false)
        super.onWindowHidden()
        traceLifecycle("windowHidden")
    }
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return !directField || inlineVisible
    }

    // A compact input view is used instead of the candidates-only window, which
    // this device clips to the navigation bar when no software input view exists.
    override fun onCreateInputView(): View = LinearLayout(this).apply {
        inlineBatch?.cancel(); inlineBatch = null; inlineVisible = false
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(android.graphics.Color.WHITE)
        // Some hardware-keyboard devices report zero bottom inset for the IME
        // even while its navigation buttons overlap the candidate window.
        val navigationClearance = (32 * resources.displayMetrics.density).toInt()
        val scroll = HorizontalScrollView(this@TitanImeService).apply {
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            candidateRow = LinearLayout(this@TitanImeService).also { addView(it) }
        }
        val bar = LinearLayout(this@TitanImeService).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            addView(scroll, LinearLayout.LayoutParams(0, -2, 1f))
            menuButton = panelButton("…") { voice.cancel(); panel = if (panel == "menu") "" else "menu"; renderPanel() }.apply {
                contentDescription = "入力メニュー"
                gravity = android.view.Gravity.CENTER
            }
            addView(menuButton, LinearLayout.LayoutParams(dp(48), -1))
        }
        addView(bar, LinearLayout.LayoutParams(-1, -2))
        autofillRow = LinearLayout(this@TitanImeService)
        addView(HorizontalScrollView(this@TitanImeService).apply { addView(autofillRow) })
        supportPanel = LinearLayout(this@TitanImeService).apply { orientation = LinearLayout.VERTICAL }
        addView(supportPanel, LinearLayout.LayoutParams(-1, -2))
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
        inputGeneration++
        lastCompositionWriteAccepted = false
        traceLifecycle("startInput restarting=$restarting package=${attribute?.packageName} type=${attribute?.inputType} field=${attribute?.fieldId} initialSelection=${attribute?.initialSelStart},${attribute?.initialSelEnd}")
        voice.cancel(); inlineVisible = false
        panel = ""; inlineBatch?.cancel(); inlineBatch = null; supportPanel?.removeAllViews(); autofillRow?.removeAllViews()
        super.onStartInput(attribute, restarting)
        engine?.setLearning(preferences.learning)
        engine?.start()
        preedit = ""
        compositionStart = -1
        selectedReading = null
        output = Output.getDefaultInstance()
        titanFnDevices.clear()
        clearPressedKeys()
        val type = attribute?.inputType ?: 0
        val variation = type and InputType.TYPE_MASK_VARIATION
        directField = (type and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT ||
            variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        restoreInitialKey(attribute, restarting)
        // FILTER editors may be passive type-to-search proxies. Do not inherit
        // an old search window when entering a fresh empty filtering connection.
        // An explicit app show request or subsequent composition may show it again.
        if (!restarting && variation == InputType.TYPE_TEXT_VARIATION_FILTER &&
            attribute?.initialSelStart == 0 && attribute.initialSelEnd == 0) requestHideSelf(0)
        traceState("startInput restarting=$restarting type=$type package=${attribute?.packageName} japanese=$japanese")
        // A launcher may keep an invisible text editor focused on its home
        // screen. A connection alone is not a request to show the IME window.
        refreshStrip(updateVisibility = false)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            attribute?.packageName == "dev.utmity.titanime.harness") {
            sendBroadcast(android.content.Intent("dev.utmity.titanime.INPUT_READY")
                .setPackage("dev.utmity.titanime.harness").putExtra("japanese", japanese))
        }
    }

    private fun restoreInitialKey(info: EditorInfo?, restarting: Boolean) {
        val receipt = initialKeyHandoff ?: return
        if (info == null || directField || !japanese || !receipt.live(android.os.SystemClock.uptimeMillis()) || info.packageName != receipt.packageName) {
            initialKeyHandoff = null; return
        }
        if (!restarting) {
            if (receipt.targetField == null && info.fieldId != receipt.sourceField && info.initialSelStart == 0 && info.initialSelEnd == 0) {
                receipt.targetField = info.fieldId
                traceLifecycle("firstKey targetArmed")
            } else initialKeyHandoff = null
            return
        }
        // The observed proxy->real editor transition first starts an empty field,
        // then restarts it after copying our emitted first letter. Require both.
        initialKeyHandoff = null
        if (receipt.targetField != info.fieldId || info.initialSelStart != 1 || info.initialSelEnd != 1) return
        val ic = currentInputConnection ?: return
        val snapshot = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        if (snapshot.partialStartOffset >= 0 || !receipt.matchesText(snapshot.text?.toString() ?: "", snapshot.startOffset, snapshot.selectionStart, snapshot.selectionEnd)) return
        if (!ic.setComposingRegion(0, 1)) return
        compositionStart = 0
        engine?.key(MozcKey.newBuilder().setKeyCode(receipt.letter.code).build())?.let { applyOutput(ic, it) }
        traceLifecycle("firstKey restoredFromOwnWrite")
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (voice.active && (oldSelStart != newSelStart || oldSelEnd != newSelEnd)) { voice.cancel(); voiceStatus = "カーソル移動で中止しました"; renderPanel() }
        traceState("selection old=$oldSelStart,$oldSelEnd new=$newSelStart,$newSelEnd composing=$candidatesStart,$candidatesEnd")
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            !directField && currentInputEditorInfo?.packageName == "dev.utmity.titanime.harness") {
            android.util.Log.i("TitanImeProbe", "selection=$oldSelStart,$oldSelEnd->$newSelStart,$newSelEnd composing=$candidatesStart,$candidatesEnd length=${preedit.length}")
        }
        if (preedit.isEmpty()) return
        val ic = currentInputConnection ?: return
        if (!synchronizeEditor(ic)) return
        if (candidatesStart >= 0 && candidatesEnd - candidatesStart == preedit.length &&
            newSelStart != newSelEnd && newSelStart in candidatesStart..candidatesEnd &&
            newSelEnd in candidatesStart..candidatesEnd && !output.preedit.hasHighlightedPosition()) {
            compositionStart = candidatesStart
            selectedReading = minOf(newSelStart, newSelEnd) - candidatesStart to
                maxOf(newSelStart, newSelEnd) - candidatesStart
            return
        }
        selectedReading = null
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
        // Outside the composing range, or selection during conversion: preserve the
        // visible text and the editor's selection, then discard only the session state.
        ic.finishComposingText()
        engine?.start()
        preedit = ""
        compositionStart = -1
        selectedReading = null
        output = Output.getDefaultInstance()
        refreshStrip()
    }

    private fun moveSegment(key: MozcKey.SpecialKey, shift: Boolean = false) {
        val ic = currentInputConnection ?: return
        if (!synchronizeEditor(ic)) return
        if (!output.preedit.hasHighlightedPosition()) return
        val event = MozcKey.newBuilder().setSpecialKey(key)
        if (shift) event.addModifierKeys(MozcKey.ModifierKey.SHIFT)
        engine?.key(event.build())?.let { applyOutput(ic, it) }
    }

    private fun refreshStrip(updateVisibility: Boolean = true) {
        candidateRow?.apply {
            removeAllViews()
            menuButton?.visibility = if (!directField || inlineVisible) View.VISIBLE else View.GONE
            addView(TextView(this@TitanImeService).apply {
                text = if (engine == null) "Mozc unavailable" else if (!japanese) "英語" else if (output.preedit.hasHighlightedPosition()) "変換" else "日本語"
                setTextColor(android.graphics.Color.DKGRAY)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
            val candidates = CandidatePresentation.candidates(output)
            val focusedId = CandidatePresentation.focusedId(output)
            if (candidates.isEmpty()) addView(TextView(this@TitanImeService).apply {
                text = preedit
                textSize = 18f
                setTextColor(android.graphics.Color.BLACK)
                setPadding(dp(12), dp(8), dp(12), dp(8))
            })
            for (candidate in candidates) {
                addView(TextView(this@TitanImeService).apply {
                    val attributes = output.allCandidateWords.candidatesList.firstOrNull { it.id == candidate.id }?.attributesList.orEmpty()
                    val correction = attributes.any { it.name == "SPELLING_CORRECTION" || it.name == "TYPING_CORRECTION" }
                    if (focusedId == candidate.id) {
                        setBackgroundColor(0xffeeeeee.toInt())
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    minimumHeight = dp(48)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    text = candidate.value +
                        (if (correction) "（訂正）" else "")
                    contentDescription = candidate.value + if (correction) " 訂正候補 ${candidate.annotation.description}" else " 候補"
                    textSize = 18f
                    setTextColor(android.graphics.Color.BLACK)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        val ic = currentInputConnection ?: return@setOnClickListener
                        if (!synchronizeEditor(ic)) return@setOnClickListener
                        val result = engine?.command(if (output.preedit.hasHighlightedPosition())
                            SessionCommand.CommandType.HIGHLIGHT_CANDIDATE else SessionCommand.CommandType.SUBMIT_CANDIDATE, candidate.id)
                            ?: return@setOnClickListener
                        applyOutput(ic, result)
                    }
                })
            }
            if (preedit.isNotEmpty() && !output.preedit.hasHighlightedPosition()) {
                addView(TextView(this@TitanImeService).apply {
                    text = "そのまま：$preedit"
                    setTextColor(android.graphics.Color.DKGRAY)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener { currentInputConnection?.let { commitComposition(it) } }
                })
            }
        }
        if (updateVisibility) {
            if (!directField && (preedit.isNotEmpty() || panel.isNotEmpty())) {
                // The input-view flag can remain true after the IME window was
                // hidden. It is not evidence that candidates are on screen.
                if (!imeWindowVisible || !isInputViewShown || window?.window?.decorView?.isShown != true) requestShowSelf(0)
            } else if (directField && !inlineVisible && isInputViewShown) requestHideSelf(0)
        }
    }

    private fun panelButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 16f; setTextColor(0xff202124.toInt())
        contentDescription = label
        setPadding(dp(12), dp(8), dp(12), dp(8))
        minimumHeight = dp(48)
        gravity = android.view.Gravity.CENTER_VERTICAL
        isClickable = true
        val value = android.util.TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) setBackgroundResource(value.resourceId)
        setOnClickListener { action() }
    }

    private fun startVoice() {
        if (directField || !currentInputStarted) return
        if (preedit.isNotEmpty()) { notice("未確定文字列を確定してから音声入力を開始してください"); return }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(android.content.Intent(this, MicrophonePermissionActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val connection = currentInputConnection ?: return
        val generation = inputGeneration
        voice.start(VoiceLanguagePolicy.primary(preferences.voiceLanguage, java.util.Locale.getDefault().toLanguageTag()), { message -> voiceStatus = message; if (panel == "voice") renderPanel() }, { text ->
            if (currentInputStarted && inputGeneration == generation && currentInputConnection === connection && !directField && imeWindowVisible) {
                connection.commitText(text, 1)
            }
            panel = ""; voiceStatus = ""; renderPanel()
        })
    }

    private fun notice(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    private fun openTransforms() {
        if (directField) return
        if (preedit.isNotEmpty()) { notice("未確定文字列を確定してから選択してください"); return }
        if (currentInputConnection?.getSelectedText(0).isNullOrEmpty()) { notice("変換する文字を選択してください"); return }
        panel = "transform"; transformIndex = 0; renderPanel(); requestShowSelf(0)
    }
    private fun transformSelection() {
        if (directField || preedit.isNotEmpty()) return
        val ic = currentInputConnection ?: return
        val snapshot = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        if (snapshot.partialStartOffset >= 0 || snapshot.startOffset != 0) { notice("選択範囲を取得できません"); return }
        val text = snapshot.text?.toString() ?: return
        val a = minOf(snapshot.selectionStart, snapshot.selectionEnd)
        val b = maxOf(snapshot.selectionStart, snapshot.selectionEnd)
        if (a < 0 || b > text.length || a == b || b - a > 10000) return
        val boundaries = android.icu.text.BreakIterator.getCharacterInstance().apply { setText(text) }
        if (!boundaries.isBoundary(a) || !boundaries.isBoundary(b)) {
            notice("文字の途中を分断する選択範囲は変換できません"); return
        }
        val selected = text.substring(a, b)
        if (ic.getSelectedText(0)?.toString() != selected) return
        val replacement = SelectionTransforms.apply(selected, transformIndex)
        if (replacement != selected) {
            ic.beginBatchEdit()
            try { ic.commitText(replacement, 1) } finally { ic.endBatchEdit() }
        }
        panel = ""; renderPanel()
    }

    private fun openHistory() {
        if (directField) return
        panel = "history"; historyQuery = ""; historyIndex = 0; renderPanel(); requestShowSelf(0)
    }
    private fun filteredHistory() = history.entries().filter { it.text.contains(historyQuery, ignoreCase = true) }
    private fun pasteHistory() {
        if (directField || !history.enabled) return
        val entry = filteredHistory().getOrNull(historyIndex) ?: return
        val ic = currentInputConnection ?: return
        // Pasting is an explicit edit. Finalize the current composition once,
        // rather than replacing it with a stale native session after the paste.
        commitComposition(ic)
        ic.commitText(entry.text, 1)
        panel = ""; renderPanel()
    }
    private fun editorAction(id: Int) {
        val ic = currentInputConnection ?: return
        finishForAppKey(ic)
        if (!ic.performContextMenuAction(id)) android.widget.Toast.makeText(this, "入力先がこの操作に対応していません", android.widget.Toast.LENGTH_SHORT).show()
        panel = ""; renderPanel()
    }
    private fun renderPanel() {
        val view = supportPanel ?: return
        view.removeAllViews()
        if (panel.isEmpty()) return
        var menuRow: LinearLayout? = null
        fun item(label: String, action: () -> Unit) {
            val button = panelButton(label, action)
            if (panel == "menu") {
                if (menuRow == null || menuRow!!.childCount == 2) {
                    menuRow = LinearLayout(this).apply { setBackgroundColor(0xfff5f5f5.toInt()) }
                    view.addView(menuRow)
                }
                menuRow!!.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
            } else view.addView(button)
        }
        if (panel == "menu") {
            if (!directField && preedit.isNotEmpty() && !output.preedit.hasHighlightedPosition()) {
                item("そのまま：$preedit") {
                    currentInputConnection?.let { commitComposition(it) }
                    panel = ""; renderPanel()
                }
            }
            if (!directField) {
                item("クリップボード履歴") { openHistory() }
                item("文字変換") { openTransforms() }
                item("音声入力") { panel = "voice"; renderPanel(); startVoice() }
            }
            item("自動入力（入力先へ要求）") {
                if (!directField) currentInputConnection?.let { finishForAppKey(it) }
                val accepted = currentInputConnection?.performContextMenuAction(android.R.id.autofill) == true
                notice(if (accepted) "入力先へ要求しました。候補の提供はアプリ・自動入力サービスに依存します" else "候補を要求できません。入力先とAndroidの自動入力設定を確認してください")
            }
            item("入力方法を切り替える") { getSystemService(android.view.inputmethod.InputMethodManager::class.java).showInputMethodPicker() }
            if (!directField) {
                item("Undo") { editorAction(android.R.id.undo) }
                item("Redo") { editorAction(android.R.id.redo) }
            }
            if (!directField && output.preedit.hasHighlightedPosition()) item("読みに戻す") {
                val ic = currentInputConnection
                if (ic != null && synchronizeEditor(ic)) engine?.key(MozcKey.newBuilder().setSpecialKey(MozcKey.SpecialKey.ESCAPE).build())?.let { applyOutput(ic, it) }
                panel = ""; renderPanel()
            }
            item("Titan IME設定") { startActivity(android.content.Intent(this, SettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else if (panel == "voice" && !directField) {
            item("認識サービスへ音声が送信される場合があります。Titanは音声を保存しません") { }
            item(voiceStatus.ifEmpty { "言語を選んで開始" }) { }
            if (!voice.active) {
                item("再度開始") { startVoice() }
            }
            item("キャンセル") { voice.cancel(); panel = ""; renderPanel() }
        } else if (panel == "transform" && !directField) {
            SelectionTransforms.labels.forEachIndexed { index, label ->
                item((if (index == transformIndex) "▶ " else "") + label) { transformIndex = index; transformSelection() }
            }
        } else if (panel == "clear") {
            item("全履歴を削除する（ピン留めも含む）") { history.clear(); panel = "history"; historyIndex = 0; renderPanel() }
        } else if (panel == "history") {
            if (!history.enabled) {
                item("履歴保存はオフです。設定から有効にできます") { }
            } else {
                item("検索：$historyQuery （文字入力／Backspace）") { }
                val entries = filteredHistory()
                historyIndex = historyIndex.coerceIn(0, maxOf(0, entries.lastIndex))
                entries.drop((historyIndex / 4) * 4).take(4).forEach { entry ->
                    val index = entries.indexOf(entry)
                    item((if (index == historyIndex) "▶ " else "") + (if (entry.pinned) "★ " else "") + entry.text.take(100).replace('\n', ' ')) {
                        historyIndex = index; pasteHistory()
                    }
                }
                val row = LinearLayout(this)
                row.addView(panelButton("ピン切替") { entries.getOrNull(historyIndex)?.let { history.pin(it.text) }; renderPanel() })
                row.addView(panelButton("削除") { entries.getOrNull(historyIndex)?.let { history.delete(it.text) }; renderPanel() })
                row.addView(panelButton("全削除…") { panel = "clear"; renderPanel() })
                view.addView(row)
                if (entries.isEmpty()) item("履歴なし") { }
            }
        }
        item("閉じる") { voice.cancel(); panel = ""; renderPanel() }
    }

    private fun handlePanel(event: KeyEvent, fn: Boolean): Boolean {
        if (panel.isEmpty()) return false
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK) { voice.cancel(); panel = ""; renderPanel(); return true }
        if (directField) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            panel = ""; renderPanel()
            return false // Preserve existing app shortcut / Titan Alt-text routing.
        }
        if (panel == "transform") {
            when (EditingBindings.direction(event, fn, true) ?: event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> transformIndex = (transformIndex + 8) % 9
                KeyEvent.KEYCODE_DPAD_DOWN -> transformIndex = (transformIndex + 1) % 9
                KeyEvent.KEYCODE_ENTER -> { transformSelection(); return true }
                else -> return true
            }
            renderPanel(); return true
        }
        if (panel != "history") return false
        val direction = EditingBindings.direction(event, fn, true) ?: event.keyCode
        when (direction) {
            KeyEvent.KEYCODE_DPAD_UP -> historyIndex--
            KeyEvent.KEYCODE_DPAD_DOWN -> historyIndex++
            KeyEvent.KEYCODE_ENTER -> { pasteHistory(); return true }
            KeyEvent.KEYCODE_DEL -> { if (historyQuery.isNotEmpty()) historyQuery = historyQuery.dropLast(Character.charCount(historyQuery.codePointBefore(historyQuery.length))); historyIndex = 0 }
            else -> {
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || fn || event.unicodeChar < 32) return false
                historyQuery += String(Character.toChars(event.unicodeChar)); historyIndex = 0
            }
        }
        renderPanel(); return true
    }

    private fun deleteSelectedReading(ic: InputConnection) {
        val (start, end) = selectedReading ?: return
        selectedReading = null
        val mozc = engine ?: return
        var result = mozc.moveCursor(preedit.codePointCount(0, end))
        repeat(preedit.codePointCount(start, end)) {
            result = mozc.key(MozcKey.newBuilder().setSpecialKey(MozcKey.SpecialKey.BACKSPACE).build())
        }
        applyOutput(ic, result)
    }

    private fun applyOutput(ic: InputConnection, result: Output) {
        traceRevision++
        traceState("output consumed=${result.consumed} result=${result.hasResult()} next=${result.preedit.segmentList.sumOf { it.value.length }} window=${result.candidateWindow.candidateCount} all=${result.allCandidateWords.candidatesCount}")
        selectedReading = null
        writingBatch = true
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
                        // The editor owns its colors (including dark themes).
                        // Mark the focused segment without painting behind its text.
                        styled.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                            offset, offset + segment.value.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        styled.setSpan(android.text.style.UnderlineSpan(),
                            offset, offset + segment.value.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    offset += segment.value.length
                }
                lastCompositionWriteAccepted = ic.setComposingText(styled, 1)
                traceState("setComposing next=${next.length}")
                preedit = next
                output = result
                if (compositionStart >= 0) {
                    val cursor = next.offsetByCodePoints(0, result.preedit.cursor.coerceIn(0, next.codePointCount(0, next.length)))
                    // Keep text and caret in one batch so an intermediate end
                    // selection cannot move the Mozc cursor back to the end.
                    // Do not read a potentially stale editor snapshot here.
                    if (cursor != next.length) ic.setSelection(compositionStart + cursor, compositionStart + cursor)
                }
            }
            else {
                if (preedit.isNotEmpty() && !result.hasResult()) ic.setComposingText("", 1)
                ic.finishComposingText()
                compositionStart = -1
            }
            preedit = next
            output = result
        } finally { ic.endBatchEdit(); writingBatch = false; traceState("endBatch") }
        refreshStrip()
    }

    /** An editor may normalize setComposingText without changing selection or
     * dropping its composing range. Compare actual text, not just range length.
     * Missing/partial snapshots are unknown, never evidence to erase text. */
    private fun synchronizeEditor(ic: InputConnection): Boolean {
        // Editors may publish their snapshot only when endBatchEdit completes.
        // An intermediate snapshot is not evidence of external normalization.
        if (writingBatch) return true
        if (preedit.isEmpty() || compositionStart < 0) return true
        val snapshot = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return true
        val text = snapshot.text ?: return true
        val start = compositionStart - snapshot.startOffset
        val end = start + preedit.length
        traceState("snapshot length=${text.length} offset=${snapshot.startOffset} selection=${snapshot.selectionStart},${snapshot.selectionEnd} range=$start,$end match=${start >= 0 && end <= text.length && text.subSequence(start, end).toString() == preedit}")
        if (start < 0 || end > text.length) return true
        if (text.subSequence(start, end).toString() == preedit) return true
        traceState("reset reason=snapshotMismatch finishComposing")
        // Retain the host's authoritative text/selection, discard stale engine
        // candidates, and start the next keystroke as a new composition.
        ic.finishComposingText()
        preedit = ""
        compositionStart = -1
        selectedReading = null
        output = Output.getDefaultInstance()
        engine?.start()
        refreshStrip()
        return false
    }

    private fun commitComposition(ic: InputConnection) {
        if (!synchronizeEditor(ic)) return
        if (preedit.isEmpty()) return
        // Ask Mozc to resolve pending romaji (notably final n) before kana commitment.
        // An already converted candidate must retain the user's selected spelling.
        if (!output.preedit.hasHighlightedPosition()) {
            engine?.key(MozcKey.newBuilder().setSpecialKey(MozcKey.SpecialKey.F6).build())
        }
        engine?.command(SessionCommand.CommandType.SUBMIT)?.let { applyOutput(ic, it) }
    }

    private fun finishForAppKey(ic: InputConnection) {
        if (selectedReading == null) { commitComposition(ic); return }
        // Copy/cut must retain the editor selection rather than collapse it when
        // committing Mozc's result. Preserve the selected visible reading in place.
        ic.finishComposingText()
        engine?.start()
        preedit = ""
        compositionStart = -1
        selectedReading = null
        output = Output.getDefaultInstance()
        refreshStrip()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        traceLifecycle("keyDown press=${event.downTime} repeat=${event.repeatCount}")
        if (voice.active) { voice.cancel(); voiceStatus = ""; panel = ""; renderPanel() }
        val consumed = handleKey(keyCode, event)
        traceLifecycle("keyHandled press=${event.downTime} consumed=$consumed")
        val key = event.deviceId to keyCode
        consumedKeys[key] = if (event.repeatCount == 0) consumed
            else consumed && consumedKeys[key] != false
        return consumed
    }

    private fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (isTitanFn(event)) titanFnDevices.add(event.deviceId)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            !directField && currentInputEditorInfo?.packageName == "dev.utmity.titanime.harness") {
            android.util.Log.i("TitanImeProbe", "key=$keyCode meta=${event.metaState} japanese=$japanese connected=${currentInputConnection != null}")
        }
        val ic = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        traceState("key kind=${when(keyCode) { KeyEvent.KEYCODE_SPACE -> "space"; KeyEvent.KEYCODE_ENTER -> "enter"; else -> "other" }} japanese=$japanese")
        synchronizeEditor(ic)
        if (preferences.matchesToggle(event, event.isFunctionPressed || event.deviceId in titanFnDevices)) {
            if (event.repeatCount == 0 && engine != null && !directField) {
                if (japanese) commitComposition(ic)
                japanese = !japanese
                preferences.japanese = japanese
                refreshStrip()
            }
            return true
        }
        // Modifiers alone have no text to commit. Pass their real Down and Up to
        // the app; never synthesize an early Up while the physical key is held.
        if (KeyEvent.isModifierKey(keyCode) || isTitanFn(event) || keyCode == KeyEvent.KEYCODE_SYM ||
            keyCode == KeyEvent.KEYCODE_FUNCTION) return super.onKeyDown(keyCode, event)
        val fnHeld = event.isFunctionPressed || event.deviceId in titanFnDevices
        if (!directField && fnHeld && !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && !event.isMetaPressed &&
            preferences.clipboardKey != "off" && keyCode == (if (preferences.clipboardKey == "b") KeyEvent.KEYCODE_B else KeyEvent.KEYCODE_V)) {
            if (event.repeatCount == 0) { if (panel == "history") { panel = ""; renderPanel() } else openHistory() }
            return true
        }
        if (!directField && fnHeld && !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed && !event.isMetaPressed &&
            preferences.transformKey != "off" && keyCode == (if (preferences.transformKey == "g") KeyEvent.KEYCODE_G else KeyEvent.KEYCODE_T)) {
            if (event.repeatCount == 0) { if (panel == "transform") { panel = ""; renderPanel() } else openTransforms() }
            return true
        }
        if (handlePanel(event, fnHeld)) return true
        if (!directField && panel.isEmpty() && fnHeld && preferences.wordDelete && keyCode == KeyEvent.KEYCODE_DEL &&
            !event.isAltPressed && !event.isMetaPressed && !output.preedit.hasHighlightedPosition()) {
            finishForAppKey(ic)
            val ctrl = KeyEvent(event.downTime, event.eventTime, event.action, event.keyCode, event.repeatCount,
                event.metaState or KeyEvent.META_CTRL_ON)
            EditingBindings.send(ic, KeyEvent.KEYCODE_DEL, ctrl)
            return true
        }
        val navigation = EditingBindings.direction(event, fnHeld, preferences.editing, preferences.editingLayout)
        if (navigation != null && !directField && !output.preedit.hasHighlightedPosition()) {
            if (preedit.isNotEmpty() && !event.isCtrlPressed && !event.isShiftPressed &&
                navigation in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
                engine?.key(MozcKey.newBuilder().setSpecialKey(if (navigation == KeyEvent.KEYCODE_DPAD_LEFT)
                    MozcKey.SpecialKey.LEFT else MozcKey.SpecialKey.RIGHT).build())?.let { applyOutput(ic, it) }
            } else {
                if (preedit.isNotEmpty() && (event.isCtrlPressed || navigation in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN))) finishForAppKey(ic)
                EditingBindings.send(ic, navigation, event)
            }
            return true
        }
        val altText = PhysicalKeyboardPolicy.altText(event, fnHeld, preferences.altText)
        if (event.isCtrlPressed || event.isMetaPressed || (event.isAltPressed && altText == null)) {
            if (japanese && !directField) finishForAppKey(ic)
            return super.onKeyDown(keyCode, event)
        }
        if (japanese && !directField && engine != null && selectedReading != null) {
            val character = event.unicodeChar
            val modified = event.isAltPressed || event.isSymPressed || event.isFunctionPressed || event.deviceId in titanFnDevices
            val replacement = !event.isCtrlPressed && !event.isMetaPressed && character in 33..126 &&
                (!modified || altText != null || !character.toChar().isLetterOrDigit())
            if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL || replacement) {
                deleteSelectedReading(ic)
                if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) return true
            } else if (!event.isShiftPressed && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                val range = selectedReading!!
                val offset = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) range.first else range.second
                selectedReading = null
                engine?.moveCursor(preedit.codePointCount(0, offset))?.let { applyOutput(ic, it) }
                return true
            }
        }
        if (japanese && !directField && output.preedit.hasHighlightedPosition()) {
            val action = preferences.segmentBindings().resolve(event, event.isFunctionPressed || event.deviceId in titanFnDevices)
            if (action != null) {
                moveSegment(if (action == SegmentAction.PREVIOUS || action == SegmentAction.SHRINK)
                    MozcKey.SpecialKey.LEFT else MozcKey.SpecialKey.RIGHT,
                    action == SegmentAction.SHRINK || action == SegmentAction.EXPAND)
                return true
            }
        }
        if (altText != null) {
            if (japanese && !directField && engine != null && !altText.toChar().isDigit()) {
                applyOutput(ic, engine!!.key(MozcKey.newBuilder().setKeyCode(altText).build()))
            } else {
                if (japanese && !directField) commitComposition(ic)
                ic.commitText(altText.toChar().toString(), 1)
            }
            return true
        }
        if (japanese && !directField && engine != null) {
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
            if (japanese && preedit.isNotEmpty()) finishForAppKey(ic)
            return super.onKeyDown(keyCode, event)
        }
        if (!japanese || directField || engine == null) return super.onKeyDown(keyCode, event)
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
            KeyEvent.KEYCODE_FORWARD_DEL -> MozcKey.SpecialKey.DEL
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
                val info = currentInputEditorInfo
                val source = if (preedit.isEmpty() && c in 'a'..'z' && event.repeatCount == 0 &&
                    !event.isShiftPressed && !imeWindowVisible && info != null &&
                    info.inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_FILTER)
                    ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) else null
                val eligible = source != null && source.text?.isEmpty() == true && source.startOffset == 0 &&
                    source.selectionStart == 0 && source.selectionEnd == 0 && source.partialStartOffset < 0
                initialKeyHandoff = null
                val result = engine!!.key(MozcKey.newBuilder().setKeyCode(c.lowercaseChar().code).build())
                applyOutput(ic, result)
                if (eligible && lastCompositionWriteAccepted && result.consumed && !result.hasResult() && preedit == c.toString()) {
                    initialKeyHandoff = InitialKeyHandoff(info!!.packageName, info.fieldId, c,
                        android.os.SystemClock.uptimeMillis(), event.deviceId to event.keyCode)
                    traceLifecycle("firstKey receiptCreated")
                }
                return result.consumed
            }
            if (preedit.isNotEmpty()) {
                commitComposition(ic)
                return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
