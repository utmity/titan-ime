package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class RawRomajiTraceTest {
    @Test fun exactSpellingAndCaseSurviveKanaComposition() {
        val trace = RawRomajiTrace()
        trace.append('s', true); trace.append('h', false); trace.append('i', false)
        assertEquals("shi", trace.candidate)
        trace.invalidate()
        trace.append('s', true); trace.append('i', false)
        assertEquals("si", trace.candidate)
    }

    @Test fun invalidatedTraceCannotMisrepresentEditedReading() {
        val trace = RawRomajiTrace()
        trace.append('n', true); trace.append('i', false)
        trace.invalidate()
        trace.append('h', false)
        assertNull(trace.candidate)
        trace.append('N', true)
        assertEquals("N", trace.candidate)
    }

    @Test fun longOrNonRomanInputDoesNotProduceMisleadingCandidate() {
        val trace = RawRomajiTrace()
        trace.append('k', true)
        trace.append('!', false)
        assertNull(trace.candidate)
        trace.append('a', true)
        repeat(129) { trace.append('a', false) }
        assertNull(trace.candidate)
    }
}
