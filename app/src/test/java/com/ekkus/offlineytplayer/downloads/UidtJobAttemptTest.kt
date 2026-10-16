package com.ekkus.offlineytplayer.downloads

import android.app.job.JobParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UidtJobAttemptTest {
    @Test
    fun stoppedAttemptNeverSettlesAfterNativeWorkerReturns() {
        val attempt = UidtJobAttempt(onStopFailure = {}, stopNative = {})
        var settlements = 0
        assertTrue(attempt.stop())
        assertFalse(attempt.stop())
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertEquals(0, settlements)
    }

    @Test
    fun finishedAttemptSettlesOnceAndCannotBeStoppedLater() {
        val attempt = UidtJobAttempt(onStopFailure = {}, stopNative = {})
        var settlements = 0
        assertTrue(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.stop())
        assertEquals(1, settlements)
    }

    @Test
    fun replacementAttemptWaitsUntilStoppedNativeWriterHasExited() {
        val previous = UidtJobAttempt(onStopFailure = {}, stopNative = {})
        val next = UidtJobAttempt(onStopFailure = {}, stopNative = {})
        val waiting = java.util.concurrent.CountDownLatch(1)
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val thread = Thread {
            waiting.countDown()
            completed.set(next.awaitPredecessor(previous))
        }
        thread.start()
        assertTrue(waiting.await(1, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse(completed.get())
        previous.stop()
        previous.markWorkerExited()
        thread.join(2_000)
        assertFalse(thread.isAlive)
        assertTrue(completed.get())
    }

    @Test
    fun stoppedReplacementDoesNotRunAfterPriorWriterExits() {
        val previous = UidtJobAttempt(onStopFailure = {}, stopNative = {})
        val next = UidtJobAttempt(onStopFailure = {}, stopNative = {})
        next.stop()
        previous.markWorkerExited()
        assertFalse(next.awaitPredecessor(previous))
    }

    @Test
    fun userAndAppStopsDoNotRequestJobSchedulerRetry() {
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_USER))
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_CANCELLED_BY_APP))
        assertTrue(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY))
        assertTrue(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_TIMEOUT))
    }
}
