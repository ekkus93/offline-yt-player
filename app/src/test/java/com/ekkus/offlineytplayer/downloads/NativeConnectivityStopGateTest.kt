package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A connectivity pause requests a cooperative stop on the active attempt,
 * not a permanent shutdown of the owning Android foreground service.
 */
class NativeConnectivityStopGateTest {
    @Test
    fun inFlightStopIsDeliveredOnceAndFreshAttemptCanRun() {
        val stopCalls = AtomicInteger()
        val gate = NativeWorkerStopGate<Any> { stopCalls.incrementAndGet() }
        val handle = Any()
        gate.attach(handle)
        gate.requestStop()
        gate.requestStop()
        assertEquals(1, stopCalls.get())
        gate.detach(handle)

        // A resumed job must use a fresh execution/stop gate.
        val freshStops = AtomicInteger()
        val freshGate = NativeWorkerStopGate<Any> { freshStops.incrementAndGet() }
        freshGate.attach(Any())
        assertEquals(0, freshStops.get())
        freshGate.requestStop()
        assertEquals(1, freshStops.get())
    }

    @Test
    fun stopRacingWithHandleAttachmentIsNotLost() {
        repeat(50) {
            val delivered = AtomicInteger()
            val gate = NativeWorkerStopGate<Any> { delivered.incrementAndGet() }
            val go = CountDownLatch(1)
            val done = CountDownLatch(2)
            val handle = Any()
            val attaching = thread(start = true) {
                try {
                    check(go.await(5, TimeUnit.SECONDS))
                    gate.attach(handle)
                } finally {
                    done.countDown()
                }
            }
            val stopping = thread(start = true) {
                try {
                    check(go.await(5, TimeUnit.SECONDS))
                    gate.requestStop()
                } finally {
                    done.countDown()
                }
            }
            go.countDown()
            check(done.await(5, TimeUnit.SECONDS)) { "controlled native stop race timed out" }
            attaching.join()
            stopping.join()
            assertEquals("stop missed in race iteration $it", 1, delivered.get())
            gate.detach(handle)
        }
    }
}
