package com.ekkus.offlineytplayer.downloads

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWorkerStopGateTest {
    private class Handle {
        val closed = AtomicBoolean(false)
        val stopCount = AtomicInteger()
    }

    @Test
    fun stopBeforeAttachSignalsNewHandleExactlyOnce() {
        val handle = Handle()
        val gate = NativeWorkerStopGate<Handle> {
            assertFalse(it.closed.get())
            it.stopCount.incrementAndGet()
        }
        gate.requestStop()
        gate.attach(handle)
        gate.requestStop()
        assertEquals(1, handle.stopCount.get())
        gate.detach(handle)
        handle.closed.set(true)
        gate.requestStop()
        assertEquals(1, handle.stopCount.get())
    }

    @Test
    fun stopAfterDetachNeverTouchesClosedHandle() {
        val handle = Handle()
        val gate = NativeWorkerStopGate<Handle> {
            assertFalse(it.closed.get())
            it.stopCount.incrementAndGet()
        }
        gate.attach(handle)
        gate.detach(handle)
        handle.closed.set(true)
        gate.requestStop()
        assertEquals(0, handle.stopCount.get())
    }

    @Test
    fun concurrentStopAndDetachNeverSignalClosedHandle() {
        repeat(200) {
            val handle = Handle()
            val gate = NativeWorkerStopGate<Handle> {
                assertFalse("native stop called on closed handle", it.closed.get())
                it.stopCount.incrementAndGet()
            }
            gate.attach(handle)
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val stopper = Thread {
                try {
                    start.await()
                    gate.requestStop()
                } finally {
                    done.countDown()
                }
            }
            val closer = Thread {
                try {
                    start.await()
                    gate.detach(handle)
                    handle.closed.set(true)
                } finally {
                    done.countDown()
                }
            }
            stopper.start()
            closer.start()
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(handle.stopCount.get() in 0..1)
            stopper.join(1_000)
            closer.join(1_000)
        }
    }
}
