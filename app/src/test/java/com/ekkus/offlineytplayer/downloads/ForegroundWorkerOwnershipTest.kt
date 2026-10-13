package com.ekkus.offlineytplayer.downloads

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWorkerOwnershipTest {
    @Test
    fun timeoutStopsActiveNativeWriterExactlyOnceAndRejectsNewWork() {
        val owner = ForegroundWorkerOwnership()
        val stops = AtomicInteger()
        val callback: () -> Unit = { stops.incrementAndGet() }
        assertTrue(owner.tryBegin(callback))
        owner.stop()
        owner.stop()
        owner.finish(callback)
        assertTrue(owner.isStopped)
        assertEquals(1, stops.get())
        assertFalse(owner.tryBegin { stops.incrementAndGet() })
        assertEquals(1, stops.get())
    }

    @Test
    fun nativeStopGatewayExceptionCannotEscapeForegroundServiceCallback() {
        var stopSignals = 0
        var boundedWarnings = 0
        val owner = ForegroundWorkerOwnership(onStopFailure = { boundedWarnings++ })
        val stopWorker: () -> Unit = {
            stopSignals++
            throw IllegalStateException("synthetic signed gateway error")
        }

        assertTrue(owner.tryBegin(stopWorker))
        owner.stop()
        owner.stop()
        owner.finish(stopWorker)
        assertTrue(owner.isStopped)
        assertFalse(owner.tryBegin(stopWorker))
        assertEquals(1, stopSignals)
        assertEquals(1, boundedWarnings)
    }

    @Test
    fun throwingStopDuringBeginStopRaceIsContainedAndAtMostOnce() {
        repeat(100) {
            val owner = ForegroundWorkerOwnership()
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val signals = AtomicInteger()
            val failures = AtomicInteger()
            val callback: () -> Unit = {
                signals.incrementAndGet()
                throw IllegalStateException("synthetic native stop failure")
            }
            val launcher = Thread {
                try {
                    start.await()
                    if (owner.tryBegin(callback)) owner.finish(callback)
                } catch (_: Exception) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
            val stopper = Thread {
                try {
                    start.await()
                    owner.stop()
                } catch (_: Exception) {
                    failures.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
            launcher.start()
            stopper.start()
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(0, failures.get())
            assertTrue(signals.get() in 0..1)
            assertTrue(owner.isStopped)
            launcher.join(1_000)
            stopper.join(1_000)
        }
    }

    @Test
    fun stopBeforeLaunchCannotStartNativeWriter() {
        val owner = ForegroundWorkerOwnership()
        val stops = AtomicInteger()
        owner.stop()
        assertFalse(owner.tryBegin { stops.incrementAndGet() })
        assertEquals(0, stops.get())
    }

    @Test
    fun completedWriterIsNotStoppedAndSecondWriterCannotOverlap() {
        val owner = ForegroundWorkerOwnership()
        val firstStops = AtomicInteger()
        val secondStops = AtomicInteger()
        val first: () -> Unit = { firstStops.incrementAndGet() }
        val second: () -> Unit = { secondStops.incrementAndGet() }
        assertTrue(owner.tryBegin(first))
        assertFalse(owner.tryBegin(second))
        owner.finish(first)
        assertTrue(owner.tryBegin(second))
        owner.finish(second)
        owner.stop()
        assertEquals(0, firstStops.get())
        assertEquals(0, secondStops.get())
    }

    @Test
    fun concurrentTimeoutAndNativeLaunchSignalAtMostOnce() {
        repeat(200) {
            val owner = ForegroundWorkerOwnership()
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val stops = AtomicInteger()
            val callback: () -> Unit = { stops.incrementAndGet() }
            val launcher = Thread {
                try {
                    start.await()
                    if (owner.tryBegin(callback)) owner.finish(callback)
                } finally {
                    done.countDown()
                }
            }
            val stopper = Thread {
                try {
                    start.await()
                    owner.stop()
                } finally {
                    done.countDown()
                }
            }
            launcher.start()
            stopper.start()
            start.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(stops.get() in 0..1)
            assertTrue(owner.isStopped)
            assertFalse(owner.tryBegin(callback))
            launcher.join(1_000)
            stopper.join(1_000)
        }
    }
}
