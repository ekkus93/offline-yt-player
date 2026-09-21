package com.ekkus.offlineytplayer.playback

import androidx.media3.common.MimeTypes
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
    fun playbackRequestCarriesSplitAudioAssetForSessionPlayback() {
        val request = LocalPlaybackPolicy.playbackRequestFor(
            LocalPlaybackAsset(
                videoPath = "/library/video-1080p.mp4",
                audioPath = "/library/audio-128k.m4a",
                title = "Fixture title",
                startPositionMs = 42_000L,
            ),
        )

        assertEquals("/library/video-1080p.mp4", request.videoPath)
        assertEquals("/library/audio-128k.m4a", request.audioPath)
        assertEquals(42_000L, request.startPositionMs)
    }

    @Test
    fun playbackRequestCarriesLocalSubtitleTracks() {
        val request = LocalPlaybackPolicy.playbackRequestFor(
            LocalPlaybackAsset(
                videoPath = "/library/video-1080p.mp4",
                title = "Fixture title",
                subtitleTracks = listOf(
                    LocalSubtitleTrack(
                        path = "/library/subtitles/en.vtt",
                        language = "en-US",
                        label = "English",
                        mimeType = MimeTypes.TEXT_VTT,
                    ),
                ),
            ),
        )

        assertEquals(1, request.subtitleTracks.size)
        assertEquals("/library/subtitles/en.vtt", request.subtitleTracks.single().path)
        assertEquals(
            listOf("English"),
            LocalPlaybackPolicy.availableSubtitleLabels(
                LocalPlaybackAsset(
                    videoPath = "/library/video-1080p.mp4",
                    title = "Fixture title",
                    subtitleTracks = request.subtitleTracks,
                ),
            ),
        )
    }

    @Test
    fun playbackRequestCarriesMultipleLocalAudioTracks() {
        val asset = LocalPlaybackAsset(
            videoPath = "/library/video-1080p.mp4",
            title = "Fixture title",
            audioTracks = listOf(
                LocalAudioTrack(path = "/library/audio-en.m4a", language = "en", label = "English"),
                LocalAudioTrack(path = "/library/audio-es.m4a", language = "es", label = "Spanish"),
            ),
        )
        val request = LocalPlaybackPolicy.playbackRequestFor(asset)

        assertEquals(2, request.audioTracks.size)
        assertEquals("/library/audio-en.m4a", request.audioTracks.first().path)
        assertEquals(listOf("English", "Spanish"), LocalPlaybackPolicy.availableAudioLabels(asset))
        assertTrue(LocalPlaybackPolicy.shouldEnableAudioSelection(asset))
    }

    @Test
    fun playbackRequestNormalizesRestoredStartPosition() {
        val request = LocalPlaybackPolicy.playbackRequestFor(
            LocalPlaybackAsset(
                videoPath = "/library/video-1080p.mp4",
                title = "Fixture title",
                startPositionMs = 12_345L,
            ),
        )

        assertEquals(12_345L, request.startPositionMs)
        assertEquals(0L, LocalPlaybackPolicy.restoredStartPosition(-1L))
    }

    @Test
    fun positionPersistenceUsesBoundedCadence() {
        assertFalse(LocalPlaybackPolicy.shouldPersistPosition(
            lastPersistedPositionMs = 10_000L,
            currentPositionMs = 12_000L,
            durationMs = 120_000L,
        ))
        assertTrue(LocalPlaybackPolicy.shouldPersistPosition(
            lastPersistedPositionMs = 10_000L,
            currentPositionMs = 15_000L,
            durationMs = 120_000L,
        ))
    }

    @Test
    fun stopPersistenceResetsCompletedPlaybackAndClampsPositions() {
        assertEquals(42_000L, LocalPlaybackPolicy.persistedPositionForStop(
            positionMs = 42_000L,
            durationMs = 120_000L,
        ))
        assertEquals(0L, LocalPlaybackPolicy.persistedPositionForStop(
            positionMs = 95_000L,
            durationMs = 120_000L,
        ))
        assertEquals(0L, LocalPlaybackPolicy.persistedPositionForStop(
            positionMs = 200_000L,
            durationMs = 120_000L,
        ))
        assertEquals(0L, LocalPlaybackPolicy.persistedPositionForStop(
            positionMs = -50L,
            durationMs = 120_000L,
        ))
    }

    @Test
    fun audioSelectionIsDisabledWhenOneOrZeroTracksAreAvailable() {
        assertFalse(LocalPlaybackPolicy.shouldEnableAudioSelection(
            LocalPlaybackAsset(videoPath = "/library/video.mp4", title = "Fixture title"),
        ))
        assertFalse(LocalPlaybackPolicy.shouldEnableAudioSelection(
            LocalPlaybackAsset(
                videoPath = "/library/video.mp4",
                title = "Fixture title",
                audioTracks = listOf(LocalAudioTrack(path = "/library/audio-en.m4a", language = "en")),
            ),
        ))
    }

    @Test
    fun localAudioTracksRejectRemotePaths() {
        expectIllegalArgument("remote playback URIs are forbidden") {
            LocalPlaybackPolicy.playbackRequestFor(
                LocalPlaybackAsset(
                    videoPath = "/library/video.mp4",
                    title = "Fixture title",
                    audioTracks = listOf(LocalAudioTrack(path = "https://example.invalid/audio.m4a")),
                ),
            )
        }
    }

    @Test
    fun subtitleTracksRejectRemotePathsAndUnsupportedMimeTypes() {
        expectIllegalArgument("remote playback URIs are forbidden") {
            LocalPlaybackPolicy.playbackRequestFor(
                LocalPlaybackAsset(
                    videoPath = "/library/video.mp4",
                    title = "Fixture title",
                    subtitleTracks = listOf(
                        LocalSubtitleTrack(
                            path = "https://example.invalid/en.vtt",
                            language = "en",
                            mimeType = MimeTypes.TEXT_VTT,
                        ),
                    ),
                ),
            )
        }
        expectIllegalArgument("unsupported subtitle mime type") {
            LocalPlaybackPolicy.playbackRequestFor(
                LocalPlaybackAsset(
                    videoPath = "/library/video.mp4",
                    title = "Fixture title",
                    subtitleTracks = listOf(
                        LocalSubtitleTrack(
                            path = "/library/en.txt",
                            language = "en",
                            mimeType = "text/plain",
                        ),
                    ),
                ),
            )
        }
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
