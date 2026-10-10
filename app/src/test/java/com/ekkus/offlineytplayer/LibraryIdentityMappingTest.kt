package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.*
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIdentityMappingTest {
    private fun item(id: String) = CoreLibraryItem(id, CoreSourceIdentity("fixture", id, null), "Fixture", 1000, "720p", 1, 0, true)
    private fun asset(id: String) = CoreLibraryPlaybackAsset(id, "fixture.mp4", null, true, null)
    private fun map(items: List<CoreLibraryItem>, assets: List<CoreLibraryPlaybackAsset>) =
        CoreGatewayResult(value = items, error = null).toLibraryScreenState(
            File("."), CoreGatewayResult(value = assets, error = null),
        )

    @Test fun duplicateLibraryIdsFailClosed() {
        assertTrue(map(listOf(item("same"), item("same")), listOf(asset("same"))) is LibraryScreenState.Failed)
    }

    @Test fun blankLibraryIdsFailClosed() {
        assertTrue(map(listOf(item(" ")), listOf(asset(" "))) is LibraryScreenState.Failed)
    }

    @Test fun duplicatePlaybackIdsFailClosed() {
        assertTrue(map(listOf(item("one")), listOf(asset("one"), asset("one"))) is LibraryScreenState.Failed)
    }

    @Test fun blankPlaybackIdsFailClosed() {
        assertTrue(map(listOf(item("one")), listOf(asset("one"), asset(" "))) is LibraryScreenState.Failed)
    }

    @Test fun validDistinctIdsRemainReady() {
        assertTrue(map(listOf(item("one")), listOf(asset("one"))) is LibraryScreenState.Ready)
    }
}
