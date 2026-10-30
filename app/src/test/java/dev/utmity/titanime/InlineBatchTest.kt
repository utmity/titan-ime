package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class InlineBatchTest {
    @Test fun completionOrderDoesNotChangeRanking() {
        val batch = InlineBatch<String>(3)
        batch.complete(2, "third"); batch.complete(0, "first"); batch.complete(1, null)
        assertEquals(listOf("first", "third"), batch.ordered()); assertTrue(batch.finished())
        assertFalse(batch.complete(0, "duplicate"))
    }
    @Test fun hideOrReplacementRejectsLateResultsAndReleasesViews() {
        val old = InlineBatch<String>(2); old.complete(0, "old"); old.cancel()
        assertFalse(old.complete(1, "late")); assertTrue(old.ordered().isEmpty())
        val fresh = InlineBatch<String>(1); assertTrue(fresh.complete(0, "new"))
        assertEquals(listOf("new"), fresh.ordered())
    }
    @Test fun allFailedInflationsFinishWithoutContent() {
        val batch = InlineBatch<String>(2)
        batch.complete(0, null); assertFalse(batch.finished()); batch.complete(1, null)
        assertTrue(batch.finished()); assertTrue(batch.ordered().isEmpty())
    }
}
