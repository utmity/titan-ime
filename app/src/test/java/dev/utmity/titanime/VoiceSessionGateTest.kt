package dev.utmity.titanime

import org.junit.Assert.*
import org.junit.Test

class VoiceSessionGateTest {
    @Test fun duplicateResultCannotInsertTwice() {
        val gate = VoiceSessionGate(); val token = gate.token()
        assertTrue(gate.consume(token)); assertFalse(gate.consume(token))
    }
    @Test fun cancellationInvalidatesOldResultWithoutInvalidatingNewSession() {
        val gate = VoiceSessionGate(); val old = gate.token()
        gate.invalidate(); val next = gate.token()
        assertFalse(gate.consume(old)); assertTrue(gate.consume(next))
    }
}
