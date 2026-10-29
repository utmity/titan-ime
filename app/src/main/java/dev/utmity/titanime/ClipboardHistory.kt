package dev.utmity.titanime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Private, bounded, opt-in text history. Never fed by autofill or editor text. */
internal class ClipboardHistory(context: Context) {
    private val store = context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
    data class Entry(val text: String, val pinned: Boolean)
    var enabled: Boolean
        get() = store.getBoolean("enabled", false)
        set(value) { store.edit().putBoolean("enabled", value).apply() }
    var limit: Int
        get() = store.getInt("limit", 20).coerceIn(5, 100)
        set(value) { store.edit().putInt("limit", value.coerceIn(5, 100)).apply(); save(entries()) }
    fun entries(): List<Entry> = try {
        val array = JSONArray(store.getString("entries", "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.map { Entry(it.getString("text"), it.optBoolean("pin")) }
    } catch (_: org.json.JSONException) { emptyList() }
    private fun save(values: List<Entry>) {
        val array = JSONArray()
        values.distinctBy { it.text }.sortedByDescending { it.pinned }.take(limit).forEach {
            array.put(JSONObject().put("text", it.text).put("pin", it.pinned))
        }
        store.edit().putString("entries", array.toString()).apply()
    }
    fun capture(text: String, sensitive: Boolean) {
        if (!enabled || sensitive || text.isBlank() || text.length > 10000) return
        val old = entries()
        // A full pinned history is never silently evicted by a new copy.
        save(listOf(Entry(text, old.firstOrNull { it.text == text }?.pinned ?: false)) + old.filter { it.text != text })
    }
    fun pin(text: String) = save(entries().map { if (it.text == text) it.copy(pinned = !it.pinned) else it })
    fun delete(text: String) = save(entries().filter { it.text != text })
    fun clear() = save(emptyList())
}
