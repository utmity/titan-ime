package dev.utmity.titanime

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

/**
 * Settings access backed by an in-memory snapshot.
 *
 * The physical-key hot path reads several of these values on every keystroke,
 * and repeated SharedPreferences lookups are not free. Values are loaded from
 * the store once, served from fields afterwards, and kept current through the
 * store's own change listener, so writes made through any ImePreferences
 * instance over the same store — the settings UI, the IME service, or tests
 * writing the store directly — are reflected without re-reading the store per
 * key. Setters persist exactly as before; keys, defaults and file are unchanged.
 */
internal class ImePreferences(context: Context) {
    val store = context.getSharedPreferences("ime_settings", Context.MODE_PRIVATE)

    private var loaded = false
    private var cachedVoiceLanguage = "auto"
    private var cachedTransformKey = "t"
    private var cachedEditing = true
    private var cachedClipboardKey = "v"
    private var cachedEditingLayout = "hjkl"
    private var cachedWordDelete = false
    private var cachedAltText = true
    private var cachedPrediction = true
    private var cachedCorrection = true
    private var cachedLearning = true
    private var cachedToggle = "fn"
    private var cachedSegments = "hl"
    private var cachedJapanese = false
    private var cachedToolbarMode = "always"
    private var cachedShortcutLanguage: String? = null
    private var cachedShortcutHistory: String? = null
    private var cachedShortcutTransform: String? = null
    private var cachedSegmentBindings: SegmentBindings? = null

    // Held in a field on purpose: SharedPreferences keeps only a weak
    // reference to listeners, so registration alone would not keep it alive.
    private val cacheListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        onStoreChanged(key)
    }

    init {
        store.registerOnSharedPreferenceChangeListener(cacheListener)
    }

    private fun reload() {
        cachedVoiceLanguage = store.getString("voice_language", "auto") ?: "auto"
        cachedTransformKey = store.getString("transform_key", "t") ?: "t"
        cachedEditing = store.getBoolean("editing", true)
        cachedClipboardKey = store.getString("clipboard_key", "v") ?: "v"
        cachedEditingLayout = store.getString("editing_layout", "hjkl") ?: "hjkl"
        cachedWordDelete = store.getBoolean("word_delete", false)
        cachedAltText = store.getBoolean("alt_text", true)
        cachedPrediction = store.getBoolean("prediction", true)
        cachedCorrection = store.getBoolean("correction", true)
        cachedLearning = store.getBoolean("learning", true)
        cachedToggle = store.getString("toggle", "fn") ?: "fn"
        cachedSegments = store.getString("segments", "hl") ?: "hl"
        cachedJapanese = store.getBoolean("japanese", false)
        cachedToolbarMode = store.getString("toolbar_mode", "always") ?: "always"
        cachedShortcutLanguage = store.getString("shortcut_language", null)
        cachedShortcutHistory = store.getString("shortcut_history", null)
        cachedShortcutTransform = store.getString("shortcut_transform", null)
        cachedSegmentBindings = null
        loaded = true
    }

    private fun ensureLoaded() {
        if (!loaded) reload()
    }

    private fun onStoreChanged(key: String?) {
        if (!loaded) return
        when (key) {
            null -> reload()
            "voice_language" -> cachedVoiceLanguage = store.getString(key, "auto") ?: "auto"
            "transform_key" -> cachedTransformKey = store.getString(key, "t") ?: "t"
            "editing" -> cachedEditing = store.getBoolean(key, true)
            "clipboard_key" -> cachedClipboardKey = store.getString(key, "v") ?: "v"
            "editing_layout" -> cachedEditingLayout = store.getString(key, "hjkl") ?: "hjkl"
            "word_delete" -> cachedWordDelete = store.getBoolean(key, false)
            "alt_text" -> cachedAltText = store.getBoolean(key, true)
            "prediction" -> cachedPrediction = store.getBoolean(key, true)
            "correction" -> cachedCorrection = store.getBoolean(key, true)
            "learning" -> cachedLearning = store.getBoolean(key, true)
            "toggle" -> cachedToggle = store.getString(key, "fn") ?: "fn"
            "segments" -> {
                cachedSegments = store.getString(key, "hl") ?: "hl"
                cachedSegmentBindings = null
            }
            "japanese" -> cachedJapanese = store.getBoolean(key, false)
            "toolbar_mode" -> cachedToolbarMode = store.getString(key, "always") ?: "always"
            "shortcut_language" -> cachedShortcutLanguage = store.getString(key, null)
            "shortcut_history" -> cachedShortcutHistory = store.getString(key, null)
            "shortcut_transform" -> cachedShortcutTransform = store.getString(key, null)
        }
    }

    var voiceLanguage: String
        get() { ensureLoaded(); return cachedVoiceLanguage }
        set(value) { cachedVoiceLanguage = value; store.edit().putString("voice_language", value).apply() }
    var transformKey: String
        get() { ensureLoaded(); return cachedTransformKey }
        set(value) { cachedTransformKey = value; store.edit().putString("transform_key", value).apply() }
    var editing: Boolean
        get() { ensureLoaded(); return cachedEditing }
        set(value) { cachedEditing = value; store.edit().putBoolean("editing", value).apply() }
    var clipboardKey: String
        get() { ensureLoaded(); return cachedClipboardKey }
        set(value) { cachedClipboardKey = value; store.edit().putString("clipboard_key", value).apply() }
    var editingLayout: String
        get() { ensureLoaded(); return cachedEditingLayout }
        set(value) { cachedEditingLayout = value; store.edit().putString("editing_layout", value).apply() }
    var wordDelete: Boolean
        get() { ensureLoaded(); return cachedWordDelete }
        set(value) { cachedWordDelete = value; store.edit().putBoolean("word_delete", value).apply() }
    var altText: Boolean
        get() { ensureLoaded(); return cachedAltText }
        set(value) { cachedAltText = value; store.edit().putBoolean("alt_text", value).apply() }
    var prediction: Boolean
        get() { ensureLoaded(); return cachedPrediction }
        set(value) { cachedPrediction = value; store.edit().putBoolean("prediction", value).apply() }
    var correction: Boolean
        get() { ensureLoaded(); return cachedCorrection }
        set(value) { cachedCorrection = value; store.edit().putBoolean("correction", value).apply() }
    var learning: Boolean
        get() { ensureLoaded(); return cachedLearning }
        set(value) { cachedLearning = value; store.edit().putBoolean("learning", value).apply() }
    var toggle: String
        get() { ensureLoaded(); return cachedToggle }
        set(value) { cachedToggle = value; store.edit().putString("toggle", value).apply() }
    var segments: String
        get() { ensureLoaded(); return cachedSegments }
        set(value) { cachedSegments = value; cachedSegmentBindings = null; store.edit().putString("segments", value).apply() }
    var japanese: Boolean
        get() { ensureLoaded(); return cachedJapanese }
        set(value) { cachedJapanese = value; store.edit().putBoolean("japanese", value).apply() }
    var toolbarMode: String
        get() { ensureLoaded(); return cachedToolbarMode }
        set(value) {
            require(value in setOf("always", "compose", "conversion"))
            cachedToolbarMode = value
            store.edit().putString("toolbar_mode", value).apply()
        }

    fun shortcut(action: String): ShortcutBinding? {
        require(action in setOf("language", "history", "transform"))
        ensureLoaded()
        val saved = when (action) {
            "language" -> cachedShortcutLanguage
            "history" -> cachedShortcutHistory
            else -> cachedShortcutTransform
        }
        if (saved == "off") return null
        if (saved != null) return ShortcutBinding.decode(saved)
        // Preserve existing profiles until the user explicitly records a new key.
        return when (action) {
            "language" -> when (cachedToggle) {
                "sym" -> ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.SYM)
                "alt" -> ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.ALT)
                else -> ShortcutBinding(KeyEvent.KEYCODE_SPACE, ShortcutBinding.FN)
            }
            "history" -> when (cachedClipboardKey) {
                "off" -> null
                "b" -> ShortcutBinding(KeyEvent.KEYCODE_B, ShortcutBinding.FN)
                else -> ShortcutBinding(KeyEvent.KEYCODE_V, ShortcutBinding.FN)
            }
            else -> when (cachedTransformKey) {
                "off" -> null
                "g" -> ShortcutBinding(KeyEvent.KEYCODE_G, ShortcutBinding.FN)
                else -> ShortcutBinding(KeyEvent.KEYCODE_T, ShortcutBinding.FN)
            }
        }
    }

    fun setShortcut(action: String, binding: ShortcutBinding?) {
        require(action in setOf("language", "history", "transform"))
        require(binding == null || !binding.reserved())
        val encoded = binding?.encode() ?: "off"
        ensureLoaded()
        when (action) {
            "language" -> cachedShortcutLanguage = encoded
            "history" -> cachedShortcutHistory = encoded
            else -> cachedShortcutTransform = encoded
        }
        store.edit().putString("shortcut_$action", encoded).apply()
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
        ensureLoaded()
        cachedSegmentBindings?.let { return it }
        val built = if (cachedSegments != "jk") SegmentBindings()
        else SegmentBindings(listOf(
            SegmentBinding(KeyEvent.KEYCODE_J, false, SegmentAction.PREVIOUS),
            SegmentBinding(KeyEvent.KEYCODE_K, false, SegmentAction.NEXT),
            SegmentBinding(KeyEvent.KEYCODE_U, false, SegmentAction.SHRINK),
            SegmentBinding(KeyEvent.KEYCODE_O, false, SegmentAction.EXPAND)))
        cachedSegmentBindings = built
        return built
    }
}
