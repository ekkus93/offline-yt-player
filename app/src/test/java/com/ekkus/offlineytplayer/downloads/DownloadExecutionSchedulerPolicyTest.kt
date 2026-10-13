package com.ekkus.offlineytplayer.downloads

import java.io.File
import android.app.job.JobParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadExecutionSchedulerPolicyTest {
    @Test
    fun schedulerSelectsUserInitiatedJobsOnlyOnApi34AndNewer() {
        assertEquals(
            DownloadSchedulerKind.ForegroundServiceFallback,
            DownloadExecutionSchedulerPolicy.schedulerKindForSdk(33),
        )
        assertEquals(
            DownloadSchedulerKind.UserInitiatedDataTransferJob,
            DownloadExecutionSchedulerPolicy.schedulerKindForSdk(34),
        )
        assertEquals(
            DownloadSchedulerKind.UserInitiatedDataTransferJob,
            DownloadExecutionSchedulerPolicy.schedulerKindForSdk(36),
        )
    }

    @Test
    fun schedulerSdkGatesEstimatedBytesAndUserInitiatedFlags() {
        assertFalse(DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(27))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(28))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsEstimatedNetworkBytes(34))

        assertFalse(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(33))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(34))
        assertTrue(DownloadExecutionSchedulerPolicy.supportsUserInitiatedJobFlag(36))
    }

    @Test
    fun invalidEstimatedBytesBecomeFrameworkUnknownInsteadOfCrashingUidtScheduling() {
        val unknown = android.app.job.JobInfo.NETWORK_BYTES_UNKNOWN.toLong()
        assertEquals(unknown, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(null))
        assertEquals(unknown, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(-1L))
        assertEquals(unknown, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(-2L))
        assertEquals(unknown, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(Long.MIN_VALUE))
        assertEquals(0L, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(0L))
        assertEquals(123L, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(123L))
        assertEquals(Long.MAX_VALUE, DownloadExecutionSchedulerPolicy.normalizedEstimatedNetworkBytes(Long.MAX_VALUE))
    }

    @Test
    fun api26Through33UseForegroundFallbackInsteadOfDuplicatedSchedulerState() {
        assertTrue(DownloadExecutionSchedulerPolicy.usesForegroundFallback(26))
        assertTrue(DownloadExecutionSchedulerPolicy.usesForegroundFallback(33))
        assertFalse(DownloadExecutionSchedulerPolicy.usesForegroundFallback(34))
        assertEquals(DownloadForegroundService.ACTION_SCHEDULE_WORK, "com.ekkus.offlineytplayer.download.SCHEDULE_WORK")
        assertEquals(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, DownloadUserInitiatedJobService.ExtraQueueItemId)
    }

    @Test
    fun uidtJobCarriesOnlyDurableQueueIdentityInExtras() {
        val scheduler = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt").readText()
        val extrasFunction = scheduler.substringAfter("fun durableQueueItemExtras").substringBefore("fun stableJobId")

        assertTrue(extrasFunction.contains("putString(DownloadUserInitiatedJobService.ExtraQueueItemId, queueItemId)"))
        assertFalse(extrasFunction.contains("url"))
        assertFalse(extrasFunction.contains("title"))
        assertFalse(extrasFunction.contains("provider_payload"))
    }

    @Test
    fun stableJobIdsAreDeterministicAndInsideConfiguredRange() {
        val first = DownloadExecutionSchedulerPolicy.stableJobId("queue-item-1")
        val second = DownloadExecutionSchedulerPolicy.stableJobId("queue-item-1")

        assertEquals(first, second)
        assertTrue(first >= DownloadUserInitiatedJobService.JobIdBase)
        assertTrue(first < DownloadUserInitiatedJobService.JobIdBase + DownloadUserInitiatedJobService.JobIdRange)
    }

    @Test
    fun minimumIntHashStillProducesJobIdInsideReservedRange() {
        // Java String.hashCode() for this input is Int.MIN_VALUE; Int.absoluteValue overflows.
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        val jobId = DownloadExecutionSchedulerPolicy.stableJobId("polygenelubricants")
        assertTrue(jobId >= DownloadUserInitiatedJobService.JobIdBase)
        assertTrue(jobId < DownloadUserInitiatedJobService.JobIdBase + DownloadUserInitiatedJobService.JobIdRange)
    }

    @Test
    fun hashCollisionNeverReplacesAnotherDurableTransfer() {
        // Java hashes collide, so the legacy direct hash ID would replace Aa
        // when BB was scheduled.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val firstId = DownloadExecutionSchedulerPolicy.stableJobId("Aa")
        assertEquals(firstId, DownloadExecutionSchedulerPolicy.stableJobId("BB"))
        val occupied = mapOf(firstId to "Aa")
        assertEquals(firstId, DownloadExecutionSchedulerPolicy.availableJobId("Aa", occupied))
        val secondId = DownloadExecutionSchedulerPolicy.availableJobId("BB", occupied)
        assertNotEquals(firstId, secondId)
        assertEquals(
            secondId,
            DownloadExecutionSchedulerPolicy.availableJobId("BB", occupied + (secondId!! to "BB")),
        )
        assertNotEquals(firstId, DownloadExecutionSchedulerPolicy.availableJobId("Aa", mapOf(firstId to null)))
    }

    @Test
    fun reschedulingCollisionVictimReusesItsExistingJobAfterEarlierSlotBecomesFree() {
        val firstId = DownloadExecutionSchedulerPolicy.stableJobId("Aa")
        val secondId = DownloadExecutionSchedulerPolicy.availableJobId("BB", mapOf(firstId to "Aa"))!!
        // The first collision occupant completed, leaving its slot available.
        // A reschedule of BB must reuse BB's pending job, not create a duplicate.
        assertEquals(secondId, DownloadExecutionSchedulerPolicy.availableJobId("BB", mapOf(secondId to "BB")))
    }

    @Test
    fun invalidJobExtrasReturnAlreadyFinishedWithoutDoubleSettlement() {
        // Android treats onStartJob=false as an already-completed job. Ensure
        // malformed durable IDs cannot double-settle or post a UIDT notification.
        val source = File("src/main/java/com/ekkus/offlineytplayer/downloads/DownloadExecutionScheduler.kt").readText()
        val onStart = source.substringAfter("override fun onStartJob(params: JobParameters): Boolean {")
            .substringBefore("Thread {")
        val invalidIdBranch = onStart.substringAfter("if (queueItemId.isNullOrBlank()) {")
            .substringBefore("}")
        assertTrue(invalidIdBranch.contains("return false"))
        assertFalse(invalidIdBranch.contains("jobFinished("))
        assertTrue(onStart.indexOf("if (queueItemId.isNullOrBlank())") < onStart.indexOf("setNotification("))
    }

    @Test
    fun jobIsSettledExactlyOnceWhenWorkerThrowsUnexpectedly() {
        var settlements = 0
        var executed = 0
        try {
            runAndSettleDownloadJob(
                work = { executed++; throw IllegalStateException("unexpected worker failure") },
                settle = { settlements++ },
            )
            org.junit.Assert.fail("Expected the unexpected worker failure to propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("unexpected worker failure", expected.message)
        }
        assertEquals(1, executed)
        assertEquals(1, settlements)
        runAndSettleDownloadJob(work = { executed++ }, settle = { settlements++ })
        assertEquals(2, executed)
        assertEquals(2, settlements)
    }

    @Test
    fun unexpectedUidtWorkerExceptionDoesNotEscapeAfterDurableJobSettlement() {
        var settlements = 0
        var boundedFailureReports = 0
        val failureResult = runAndSettleDownloadJobSafely(
            work = { throw IllegalStateException("private signed provider URL") },
            settle = { settlements++ },
            onUnexpectedFailure = { boundedFailureReports++ },
        )
        assertFalse(failureResult)
        assertEquals(1, settlements)
        assertEquals(1, boundedFailureReports)

        val successResult = runAndSettleDownloadJobSafely(
            work = {},
            settle = { settlements++ },
            onUnexpectedFailure = { boundedFailureReports++ },
        )
        assertTrue(successResult)
        assertEquals(2, settlements)
        assertEquals(1, boundedFailureReports)
    }

    @Test
    fun platformStopSignalsNativeOnceAndSuppressesLateJobSettlement() {
        var stopSignals = 0
        var settlements = 0
        val attempt = UidtJobAttempt { stopSignals++ }

        assertTrue(attempt.stop())
        assertFalse(attempt.stop())
        assertFalse(attempt.finishIfRunning { settlements++ })
        attempt.markWorkerExited()
        assertEquals(1, stopSignals)
        assertEquals(0, settlements)
    }

    @Test
    fun uidtStopNativeFailureCannotCrashJobServiceOrSettleRevokedAttempt() {
        var stopSignals = 0
        var boundedFailureReports = 0
        var settlements = 0
        val attempt = UidtJobAttempt(
            stopNative = {
                stopSignals++
                throw IllegalStateException("synthetic sensitive gateway failure")
            },
            onStopFailure = { boundedFailureReports++ },
        )

        assertTrue(attempt.stop())
        assertFalse(attempt.stop())
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertEquals(1, stopSignals)
        assertEquals(1, boundedFailureReports)
        assertEquals(0, settlements)

        var successfulStopFailures = 0
        val successfulAttempt = UidtJobAttempt(
            stopNative = { stopSignals++ },
            onStopFailure = { successfulStopFailures++ },
        )
        assertTrue(successfulAttempt.stop())
        assertEquals(2, stopSignals)
        assertEquals(0, successfulStopFailures)
    }

    @Test
    fun completedAttemptCannotBeStoppedOrSettledAgain() {
        var stopSignals = 0
        var settlements = 0
        val attempt = UidtJobAttempt { stopSignals++ }

        assertTrue(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.finishIfRunning { settlements++ })
        assertFalse(attempt.stop())
        assertEquals(1, settlements)
        assertEquals(0, stopSignals)
    }

    @Test
    fun replacementWaitsUntilPreviousNativeWriterHasExited() {
        val previous = UidtJobAttempt()
        val replacement = UidtJobAttempt()
        val entered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        val waiter = Thread {
            entered.countDown()
            result.set(replacement.awaitPredecessor(previous))
            completed.countDown()
        }
        waiter.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS))
            previous.markWorkerExited()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(true, result.get())
        } finally {
            previous.markWorkerExited()
            waiter.join(2_000)
        }
    }

    @Test
    fun stoppedReplacementDoesNotBeginBeforePreviousNativeWriterExits() {
        val previous = UidtJobAttempt()
        val replacement = UidtJobAttempt()
        val entered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val result = AtomicReference<Boolean>()
        val waiter = Thread {
            entered.countDown()
            result.set(replacement.awaitPredecessor(previous))
            completed.countDown()
        }
        waiter.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS))
            assertTrue(replacement.stop())
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(false, result.get())
        } finally {
            previous.markWorkerExited()
            waiter.join(2_000)
        }
    }

    @Test
    fun userAndAppRequestedUidtStopsDoNotAutomaticallyReschedule() {
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_USER))
        assertFalse(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_CANCELLED_BY_APP))
        assertTrue(UidtJobStopPolicy.shouldReschedule(JobParameters.STOP_REASON_TIMEOUT))
    }

    @Test
    fun slowNativeStopDoesNotBlockOtherJobAttemptsOrLateSettlement() {
        val registry = UidtJobAttemptRegistry<Any>()
        val firstParams = Any()
        val secondParams = Any()
        val stopEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(1)
        val first = UidtJobAttempt {
            stopEntered.countDown()
            check(releaseStop.await(5, TimeUnit.SECONDS))
        }
        var secondStops = 0
        val second = UidtJobAttempt { secondStops++ }
        registry.register(7301, firstParams, first)
        registry.register(7302, secondParams, second)
        val firstStopper = Thread { registry.stop(firstParams) }
        val secondDone = CountDownLatch(1)
        val secondStopper = Thread {
            try {
                assertTrue(registry.stop(secondParams))
            } finally {
                secondDone.countDown()
            }
        }
        firstStopper.start()
        try {
            assertTrue(stopEntered.await(2, TimeUnit.SECONDS))
            // Both the attempt and registry must publish stopped state before
            // entering a slow native callback. No late jobFinished is allowed.
            assertFalse(first.finishIfRunning { error("stale jobFinished") })
            secondStopper.start()
            assertTrue("second UIDT stop blocked on first native stop", secondDone.await(2, TimeUnit.SECONDS))
            assertEquals(1, secondStops)
        } finally {
            releaseStop.countDown()
            firstStopper.join(2_000)
            secondStopper.join(2_000)
            registry.unregister(7301, firstParams, first)
            registry.unregister(7302, secondParams, second)
        }
    }


    @Test
    fun separatelyUnparceledStopParametersStillStopTheOriginalNativeWriter() {
        val registry = UidtJobAttemptRegistry<Any>()
        val startParams = Any()
        val stoppedParams = Any() // Android may unmarshal a distinct object.
        var stops = 0
        var settled = 0
        val attempt = UidtJobAttempt { stops++ }
        registry.register(7301, startParams, attempt, "durable-job")

        assertFalse(registry.stop(stoppedParams, 7301, "another-job"))
        assertFalse(registry.stop(stoppedParams, 7302, "durable-job"))
        assertFalse(registry.stop(stoppedParams, 7301, null))
        assertTrue(registry.stop(stoppedParams, 7301, "durable-job"))
        assertFalse(registry.stop(stoppedParams, 7301, "durable-job"))
        assertFalse(attempt.finishIfRunning { settled++ })
        assertEquals(1, stops)
        assertEquals(0, settled)
        registry.unregister(7301, startParams, attempt)
    }

    @Test
    fun parcelEquivalentStaleStopNeverTargetsRegisteredReplacement() {
        val registry = UidtJobAttemptRegistry<Any>()
        val originalParams = Any()
        val replacementParams = Any()
        val recreatedOldStop = Any()
        var oldStops = 0
        var replacementStops = 0
        val old = UidtJobAttempt { oldStops++ }
        val replacement = UidtJobAttempt { replacementStops++ }

        registry.register(7301, originalParams, old, "same-durable-job")
        assertEquals(
            old,
            registry.register(7301, replacementParams, replacement, "same-durable-job"),
        )
        assertTrue(registry.stop(recreatedOldStop, 7301, "same-durable-job"))
        assertFalse(registry.stop(recreatedOldStop, 7301, "same-durable-job"))
        assertEquals(1, oldStops)
        assertEquals(0, replacementStops)
        registry.unregister(7301, originalParams, old)
        assertTrue(registry.stop(Any(), 7301, "same-durable-job"))
        assertEquals(1, replacementStops)
        registry.unregister(7301, replacementParams, replacement)
    }

    @Test
    fun exhaustedJobIdRangeFailsClosedInsteadOfReplacingAnotherJob() {
        val occupied = (DownloadUserInitiatedJobService.JobIdBase until
            DownloadUserInitiatedJobService.JobIdBase + DownloadUserInitiatedJobService.JobIdRange)
            .associateWith { "another-queue-item" }
        assertNull(DownloadExecutionSchedulerPolicy.availableJobId("new-queue-item", occupied))
    }

    @Test
    fun userInitiatedPathHasManifestPermissionAndJobService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RUN_USER_INITIATED_JOBS"))
        assertTrue(manifest.contains(".downloads.DownloadUserInitiatedJobService"))
        assertTrue(manifest.contains("android.permission.BIND_JOB_SERVICE"))
    }

    @Test
    fun stalePlatformStopCannotCancelNewAttemptReusingJobId() {
        val registry = UidtJobAttemptRegistry<Any>()
        val oldParams = Any()
        val newParams = Any()
        var oldStops = 0
        var newStops = 0
        val old = UidtJobAttempt { oldStops++ }
        val replacement = UidtJobAttempt { newStops++ }

        assertNull(registry.register(7301, oldParams, old))
        assertEquals(old, registry.register(7301, newParams, replacement))
        assertTrue(registry.stop(oldParams))
        assertEquals(1, oldStops)
        assertEquals(0, newStops)
        registry.unregister(7301, oldParams, old)
        assertFalse(registry.stop(oldParams))
        assertTrue(replacement.finishIfRunning {})
        assertEquals(0, newStops)
        registry.unregister(7301, newParams, replacement)
    }

    @Test
    fun sameJobIdWithDifferentParametersNeverStopsUnrelatedAttempt() {
        val registry = UidtJobAttemptRegistry<Any>()
        val params = Any()
        val unrelated = Any()
        var stopSignals = 0
        val attempt = UidtJobAttempt { stopSignals++ }
        registry.register(7302, params, attempt)
        assertFalse(registry.stop(unrelated))
        assertEquals(0, stopSignals)
        assertTrue(registry.stop(params))
        assertEquals(1, stopSignals)
        registry.unregister(7302, params, attempt)
    }

}
