package com.ekkus.offlineytplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecyclePublicationGateTest {
    @Test
    fun stoppedActivityRejectsQueuedRepositoryPublication() {
        val gate = LifecyclePublicationGate()
        assertNull(gate.capture())
        gate.start()
        val token = gate.capture()!!
        assertTrue(gate.permits(token))
        gate.stop()
        assertFalse(gate.permits(token))
        assertNull(gate.capture())
    }

    @Test
    fun restartedActivityRejectsOldUiQueueEvenWhenStartedAgain() {
        val gate = LifecyclePublicationGate()
        gate.start()
        val oldToken = gate.capture()!!
        gate.stop()
        gate.start()
        val freshToken = gate.capture()!!
        assertFalse(gate.permits(oldToken))
        assertTrue(gate.permits(freshToken))
    }

    @Test
    fun duplicateStartAndStopAreIdempotent() {
        val gate = LifecyclePublicationGate()
        gate.start()
        val token = gate.capture()!!
        gate.start()
        assertEquals(token, gate.capture())
        gate.stop()
        gate.stop()
        assertFalse(gate.permits(token))
    }
}
