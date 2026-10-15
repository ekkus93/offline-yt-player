package com.ekkus.offlineytplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePlaybackQualificationTest {
    @Test
    fun completedPlaybackPlanHasNoNetworkDependency() {
        val fixture = LocalPlaybackAsset(
            videoPath = "/data/user/0/com.ekkus.offlineytplayer/files/fixtures/offline.mp4",
            title = "Offline fixture",
            startPositionMs = 12_000L,
        )
        val plan = LocalPlaybackPolicy.mediaSourcePlanFor(fixture)
        assertFalse(plan.videoPath.startsWith("http"))
        assertEquals(12_000L, plan.startPositionMs)
    }

    @Test
    fun coldStartPlanRetainsSeekPauseResumeSemanticsWithoutNetwork() {
        val fixture = LocalPlaybackAsset(
            videoPath = "/library/offline-fixture.mp4",
            title = "Offline fixture",
            startPositionMs = 24_000L,
        )
        val restored = LocalPlaybackPolicy.mediaSourcePlanFor(fixture)
        assertEquals(24_000L, restored.startPositionMs)
        assertEquals(10_000L, LocalPlaybackPolicy.SkipIntervalMs)
        assertTrue(LocalPlaybackPolicy.completedByPosition(95_000L, 120_000L))
        assertFalse(LocalPlaybackPolicy.completedByPosition(80_000L, 120_000L))
    }
}
