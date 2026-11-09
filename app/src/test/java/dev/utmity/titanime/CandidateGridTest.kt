package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

/** Grid math behind the expanded candidate panel and the strip's
 * scroll-into-view behaviour. */
class CandidateGridTest {
    @Test fun columnsStayBetweenThreeAndFour() {
        assertEquals(3, CandidateGrid.columnsForWidth(320))
        assertEquals(3, CandidateGrid.columnsForWidth(360))
        assertEquals(4, CandidateGrid.columnsForWidth(440))
        assertEquals(4, CandidateGrid.columnsForWidth(480))
        assertEquals(4, CandidateGrid.columnsForWidth(1200))
    }

    @Test fun rowsAndPositionsCoverEveryIndex() {
        assertEquals(0, CandidateGrid.rowsFor(0, 4))
        assertEquals(1, CandidateGrid.rowsFor(1, 4))
        assertEquals(3, CandidateGrid.rowsFor(12, 4))
        assertEquals(43, CandidateGrid.rowsFor(169, 4))
        assertEquals(4, CandidateGrid.rowOf(17, 4))
        assertEquals(1, CandidateGrid.columnOf(17, 4))
        // Every index lands inside the computed grid exactly once.
        val columns = 4
        val seen = mutableSetOf<Pair<Int, Int>>()
        for (index in 0 until 169) {
            val position = CandidateGrid.rowOf(index, columns) to CandidateGrid.columnOf(index, columns)
            assertTrue(seen.add(position))
            assertTrue(position.first < CandidateGrid.rowsFor(169, columns))
        }
    }

    @Test fun revealKeepsVisibleItemsPut() {
        // Item 100..150 inside viewport 0..200: no movement.
        assertEquals(0, CandidateGrid.revealOffset(100, 50, 200, 0))
        // Item already visible at a scrolled position: no movement.
        assertEquals(80, CandidateGrid.revealOffset(100, 50, 200, 80))
    }

    @Test fun revealScrollsForwardAndBack() {
        // Item 300..350 below viewport 0..200: scroll so its end is visible.
        assertEquals(150, CandidateGrid.revealOffset(300, 50, 200, 0))
        // Item 10..60 above viewport 100..300: scroll back to its start.
        assertEquals(10, CandidateGrid.revealOffset(10, 50, 200, 100))
        // Margin is honoured when moving, and the result never goes negative.
        assertEquals(158, CandidateGrid.revealOffset(300, 50, 200, 0, margin = 8))
        assertEquals(0, CandidateGrid.revealOffset(4, 50, 200, 100, margin = 8))
    }

    @Test fun revealIgnoresDegenerateViewports() {
        assertEquals(42, CandidateGrid.revealOffset(300, 50, 0, 42))
    }
}
