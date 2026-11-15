package dev.utmity.titanime

/** Main-thread session status, never a persisted guess from QWERTY preferences. */
internal object InputModeStatus {
    private val listeners = mutableSetOf<() -> Unit>()
    var active: String? = null
        set(value) {
            if (field == value) return
            field = value
            listeners.toList().forEach { it() }
        }

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
}
