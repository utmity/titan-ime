package dev.utmity.titanime

/** Invalidates asynchronous callbacks after cancellation and consumes a result only once. */
internal class VoiceSessionGate {
    private var generation = 0L
    fun invalidate() { generation++ }
    fun token() = generation
    fun accepts(token: Long) = token == generation
    fun consume(token: Long): Boolean {
        if (!accepts(token)) return false
        invalidate()
        return true
    }
}
