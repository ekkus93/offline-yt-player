package com.ekkus.offlineytplayer.downloads

import android.app.job.JobParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UidtJobAttemptTest {
    @Test
    fun stoppedAttemptNeverSettlesAfterNativeWorkerReturns() {
        val attempt = UidtJobAttempt()
        var settlements = 0
        assertTrue(attempt.stop())
        assertFalse(attempt.stop())
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertEquals(0, settlements)
    }

    @Test
    fun finishedAttemptSettlesOnceAndCannotBeStoppedLater() {
        val attempt = UidtJobAttempt()
        var settlements = 0
        assertTrue(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.stop())
        assertEquals(1, settlements)
    }

    @Test
    fun userAndAppStopsDoNotRequestJobSchedulerRetry() {
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_USER))
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_CANCELLED_BY_APP))
        assertTrue(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY))
        assertTrue(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_TIMEOUT))
    }
}
