package com.ekkus.offlineytplayer.coregateway

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCoreGatewayPolicyTest {
    @Test
    fun fakeGatewaySupportsDeterministicRepositoryStateForUiTests() {
        val item = CoreLibraryItem(
            itemId = "item-1",
            source = CoreSourceIdentity("fixture", "media-1", "https://fixture.invalid/media-1"),
            displayTitle = "Fixture Item",
            durationMs = 1_000,
            qualityLabel = "720p",
            createdAtEpochMs = 7,
            playbackPositionMs = 0,
            completed = true,
        )
        val queued = CoreDownloadSnapshot(
            jobId = "job-1",
            state = CoreDownloadState.QUEUED,
            bytesDownloaded = 512,
            totalBytes = 1_024,
            attempt = 1,
            retryAtEpochMs = null,
            lastError = null,
        )
        val gateway = FakeCoreGateway(initialItems = listOf(item), initialDownloads = listOf(queued))

        assertEquals(listOf(item), gateway.listLibrary("fixture").value)
        assertEquals(item, gateway.getLibraryItem("item-1").value)
        assertEquals(true, gateway.deleteLibraryItem("item-1").value)
        assertEquals(emptyList<CoreLibraryItem>(), gateway.listLibrary().value)
        assertEquals(listOf(queued), gateway.listDownloadQueue().value)
    }

    @Test
    fun generatedDownloadStateNamesAreConvertedToAppModels() {
        assertEquals(CoreDownloadState.RETRY_WAIT, CoreDownloadState.fromGeneratedName("RetryWait"))
        assertEquals(CoreDownloadState.CANCELED, CoreDownloadState.fromGeneratedName("FfiDownloadState.Canceled"))
    }

    @Test
    fun productionGatewayIsTheOnlyLayerThatNamesGeneratedUniffiService() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt").readText()
        val controls = File("src/main/java/com/ekkus/offlineytplayer/coregateway/AppDownloadControlGateway.kt").readText()

        assertTrue(source.contains("Class.forName(\"com.ekkus.offlineytplayer.core.FfiCoreService\")"))
        assertTrue(source.contains("CoreCallDispatcher"))
        assertTrue(source.contains("checkNotMainThread()"))
        assertTrue(source.contains("FakeCoreGateway"))
        assertTrue(source.contains("CoreGatewayError"))
        assertTrue(source.contains("CoreDownloadSnapshot"))
        assertTrue(source.contains("listDownloadQueueAsync"))
        assertTrue(controls.contains("enqueueAsync"))
        assertTrue(controls.contains("resumeAsync"))
        assertTrue(controls.contains("cancelAsync"))
        assertTrue(controls.contains("retryAsync"))
        assertTrue(controls.contains("checkNotMainThread()"))
        assertFalse(source.contains("onClick = {}"))
    }
}
