package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Rmd501DurableQueueGatewayTest {
    @Test
    fun generatedDownloadStateNamesMapToEveryDurableQueueState() {
        val generatedNames = mapOf(
            "Queued" to CoreDownloadState.QUEUED,
            "Resolving" to CoreDownloadState.RESOLVING,
            "Downloading" to CoreDownloadState.DOWNLOADING,
            "Paused" to CoreDownloadState.PAUSED,
            "RetryWait" to CoreDownloadState.RETRY_WAIT,
            "Failed" to CoreDownloadState.FAILED,
            "Verifying" to CoreDownloadState.VERIFYING,
            "Completed" to CoreDownloadState.COMPLETED,
            "Canceled" to CoreDownloadState.CANCELED,
        )

        assertEquals(CoreDownloadState.entries.toSet(), generatedNames.values.toSet())
        generatedNames.forEach { (generatedName, expected) ->
            assertEquals(expected, CoreDownloadState.fromGeneratedName(generatedName))
        }
    }

    @Test
    fun coreGatewayExposesDurableQueueSnapshotsWithoutServiceLocalState() {
        val snapshots = CoreDownloadState.entries.mapIndexed { index, state ->
            CoreDownloadSnapshot(
                jobId = "job-$index",
                state = state,
                bytesDownloaded = index.toLong(),
                totalBytes = 100,
                attempt = index,
                retryAtEpochMs = if (state == CoreDownloadState.RETRY_WAIT) 42_000 else null,
                lastError = if (state == CoreDownloadState.FAILED) {
                    CoreGatewayError("NetworkTimeout", "timeout", retryable = true)
                } else {
                    null
                },
            )
        }
        val gateway = FakeCoreGateway(initialDownloads = snapshots)

        val result = gateway.listDownloadQueue()

        assertNull(result.error)
        assertEquals(snapshots.sortedBy { it.jobId }, result.value)
    }
}
