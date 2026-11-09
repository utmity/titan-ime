package dev.utmity.titanime

/** Provider ranking is independent of asynchronous remote-view completion order. */
internal class InlineBatch<T : Any>(size: Int) {
    private val values = MutableList<T?>(size) { null }
    private val finished = BooleanArray(size)
    private var cancelled = false
    fun cancel() { cancelled = true; values.fill(null) }
    fun complete(index: Int, value: T?): Boolean {
        if (cancelled || finished[index]) return false
        finished[index] = true
        values[index] = value
        return true
    }
    fun ordered(): List<T> = if (cancelled) emptyList() else values.filterNotNull()
    fun finished() = finished.all { it }
}
