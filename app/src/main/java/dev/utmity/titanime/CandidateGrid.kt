package dev.utmity.titanime

/**
 * Layout math for the expanded candidate grid and for revealing a selected
 * item inside a one-dimensional scroller (the candidate strip horizontally,
 * the grid vertically). Pure functions so the behaviour is unit-testable
 * without a device.
 */
internal object CandidateGrid {
    /** 3–4 columns: roughly one column per 110dp of available width. */
    fun columnsForWidth(widthDp: Int): Int = (widthDp / 110).coerceIn(3, 4)

    fun rowsFor(count: Int, columns: Int): Int =
        if (count <= 0 || columns <= 0) 0 else (count + columns - 1) / columns

    fun rowOf(index: Int, columns: Int): Int = if (columns <= 0) 0 else index / columns

    fun columnOf(index: Int, columns: Int): Int = if (columns <= 0) 0 else index % columns

    /**
     * The scroll offset that makes the item span [itemStart, itemStart+itemSize)
     * visible inside a viewport of [viewportSize] currently scrolled to
     * [current], keeping [margin] of slack when it has to move. Returns
     * [current] unchanged when the item is already fully visible, so steady
     * navigation does not make the scroller jump.
     */
    fun revealOffset(itemStart: Int, itemSize: Int, viewportSize: Int, current: Int, margin: Int = 0): Int {
        if (viewportSize <= 0 || itemSize < 0) return current
        val viewEnd = current + viewportSize
        val itemEnd = itemStart + itemSize
        return when {
            itemStart >= current && itemEnd <= viewEnd -> current
            itemStart < current -> maxOf(0, itemStart - margin)
            else -> maxOf(0, itemEnd - viewportSize + margin)
        }
    }
}
