package com.ekkus.offlineytplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalPlaybackPolicyTest {
    @Test
    fun adaptivePlanKeepsDistinctLocalVideoAndAudioAssets() {
        val plan = LocalPlaybackPolicy.mediaSourcePlanFor(
            LocalPlaybackAsset(
                videoPath = "/library/video-1080p.mp4",
                audioPath = "/library/audio-128k.m4a",
                title = "Fixture title",
                startPositionMs = 42_000L,
            ),
        )

        assertEquals("/library/video-1080p.mp4", plan.videoPath)
        assertEquals("/library/audio-128k.m4a", plan.audioPath)
        assertEquals(42_000L, plan.startPositionMs)
        assertTrue(plan.usesSeparateAudioVideoAssets)
    }

    @Test
    fun singleFilePlanDoesNotClaimSeparateAdaptiveAssets() {
        val plan = LocalPlaybackPolicy.mediaSourcePlanFor(
            LocalPlaybackAsset(
                videoPath = "/library/combined.mp4",
                title = "Fixture title",
            ),
        )

        assertEquals("/library/combined.mp4", plan.videoPath)
        assertEquals(null, plan.audioPath)
        assertEquals(0L, plan.startPositionMs)
        assertFalse(plan.usesSeparateAudioVideoAssets)
    }

    @Test
    fun subtitlePlanKeepsPersistedLocalTrackIdentity() {
        val subtitle = LocalSubtitleAsset(
            path = "/library/subtitles/en-US.vtt",
            mimeType = "text/vtt",
            language = "en-US",
            label = "English",
        )
        val plan = LocalPlaybackPolicy.mediaSourcePlanFor(
            LocalPlaybackAsset(
                videoPath = "/library/combined.mp4",
                title = "Fixture title",
                subtitles = listOf(subtitle),
            ),
        )

        assertEquals(listOf(subtitle), plan.subtitles)
    }

    @Test
    fun subtitlePlanRejectsRemoteTrack() {
        expectIllegalArgument("remote playback URIs are forbidden") {
            LocalPlaybackPolicy.mediaSourcePlanFor(
                LocalPlaybackAsset(
                    videoPath = "/library/combined.mp4",
                    title = "Fixture title",
                    subtitles = listOf(
                        LocalSubtitleAsset(
                            path = "https://example.invalid/en.vtt",
                            mimeType = "text/vtt",
                            language = "en",
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun subtitlePlanRejectsUnsupportedMimeType() {
        expectIllegalArgument("unsupported subtitle MIME type") {
            LocalPlaybackPolicy.mediaSourcePlanFor(
                LocalPlaybackAsset(
                    videoPath = "/library/combined.mp4",
                    title = "Fixture title",
                    subtitles = listOf(
                        LocalSubtitleAsset(
                            path = "/library/subtitles/en.ass",
                            mimeType = "text/x-ssa",
                            language = "en",
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun adaptivePlanRejectsRemoteAudioAsset() {
        expectIllegalArgument("remote playback URIs are forbidden") {
            LocalPlaybackPolicy.mediaSourcePlanFor(
                LocalPlaybackAsset(
                    videoPath = "/library/video.mp4",
                    audioPath = "https://example.invalid/audio.m4a",
                    title = "Fixture title",
                ),
            )
        }
    }

    @Test
    fun adaptivePlanRejectsSameVideoAndAudioPath() {
        expectIllegalArgument("separate audio and video assets must use distinct paths") {
            LocalPlaybackPolicy.mediaSourcePlanFor(
                LocalPlaybackAsset(
                    videoPath = "/library/combined.mp4",
                    audioPath = "/library/combined.mp4",
                    title = "Fixture title",
                ),
            )
        }
    }

    private fun expectIllegalArgument(
        expectedMessage: String,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertEquals(expectedMessage, error.message)
        }
    }
}
