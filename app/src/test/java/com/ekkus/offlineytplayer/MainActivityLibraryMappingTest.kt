package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.CoreLibraryPlaybackAsset
import com.ekkus.offlineytplayer.coregateway.CoreSourceIdentity
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLibraryMappingTest {
    private val library = CoreGatewayResult(
        listOf(
            CoreLibraryItem(
                "item-1",
                CoreSourceIdentity("fixture", "media-1", null),
                "Saved offline video",
                1_000L,
                "720p",
                1L,
                0L,
                true,
            ),
        ),
        null,
    )
    private val libraryRoot = File(".")

    @Test fun missingPlaybackGatewayDoesNotSilentlyDisablePlayableMedia() {
        assertTrue(library.toLibraryScreenState(libraryRoot, null) is LibraryScreenState.Failed)
    }

    @Test fun playbackGatewayFailureDoesNotMasqueradeAsEmptyAssets() {
        val failed = CoreGatewayResult<List<CoreLibraryPlaybackAsset>>(
            null,
            CoreGatewayError("unavailable", "private/path/should/not/be/shown", true),
        )
        val mapped = library.toLibraryScreenState(libraryRoot, failed)
        assertTrue(mapped is LibraryScreenState.Failed)
        assertTrue((mapped as LibraryScreenState.Failed).message == "Library playback metadata is unavailable.")
    }

    @Test fun missingSuccessfulPlaybackPayloadIsNotAcceptedAsEmptyAssets() {
        val malformed = CoreGatewayResult<List<CoreLibraryPlaybackAsset>>(null, null)
        assertTrue(library.toLibraryScreenState(libraryRoot, malformed) is LibraryScreenState.Failed)
    }

    @Test fun missingSuccessfulLibraryPayloadIsNotAcceptedAsEmptyLibrary() {
        val malformed = CoreGatewayResult<List<CoreLibraryItem>>(null, null)
        val assets = CoreGatewayResult<List<CoreLibraryPlaybackAsset>>(emptyList(), null)
        val mapped = malformed.toLibraryScreenState(libraryRoot, assets)
        assertTrue(mapped is LibraryScreenState.Failed)
        assertTrue((mapped as LibraryScreenState.Failed).message == "Library repository data is unavailable.")
    }

    @Test fun missingSuccessfulDownloadQueuePayloadIsNotAcceptedAsEmptyQueue() {
        val malformed = CoreGatewayResult<List<com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot>>(null, null)
        val mapped = malformed.toDownloadsScreenState(emptyMap())
        assertTrue(mapped is com.ekkus.offlineytplayer.ui.DownloadsScreenState.Failed)
        assertTrue((mapped as com.ekkus.offlineytplayer.ui.DownloadsScreenState.Failed).message == "Download queue data is unavailable.")
    }

    @Test fun validEmptyPlaybackIndexStillMapsLibraryNormally() {
        val empty = CoreGatewayResult<List<CoreLibraryPlaybackAsset>>(emptyList(), null)
        assertTrue(library.toLibraryScreenState(libraryRoot, empty) is LibraryScreenState.Ready)
    }
}
