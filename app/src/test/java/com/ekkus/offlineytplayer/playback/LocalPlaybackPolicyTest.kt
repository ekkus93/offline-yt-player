package com.ekkus.offlineytplayer.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun mediaSessionAndTrackPresentationRedactProviderControlledLabels() {
        val secret = "RMD1802_PRIVATE_PLAYBACK_LABEL"
        val asset = LocalPlaybackAsset(
            videoPath = "/library/video.mp4",
            title = "Video https://example.test/title?token=$secret",
            subtitleTracks = listOf(
                LocalSubtitleTrack(
                    path = "/library/en.vtt",
                    language = "en",
                    label = "English https://example.test/sub?token=$secret",
                    mimeType = MimeTypes.TEXT_VTT,
                ),
            ),
            audioTracks = listOf(
                LocalAudioTrack(
                    path = "/library/audio.m4a",
                    label = "Audio https://example.test/audio?token=$secret",
                ),
            ),
        )
        assertEquals(
            "Video https://example.test/title?REDACTED",
            LocalPlaybackPolicy.displayTitle(asset.title),
        )
        assertEquals(
            listOf("English https://example.test/sub?REDACTED"),
            LocalPlaybackPolicy.availableSubtitleLabels(asset),
        )
        assertEquals(
            listOf("Audio https://example.test/audio?REDACTED"),
            LocalPlaybackPolicy.availableAudioLabels(asset),
        )
        assertTrue(secret !in LocalPlaybackPolicy.displayTitle(asset.title))
        assertTrue(secret !in LocalPlaybackPolicy.availableSubtitleLabels(asset).single())
        assertTrue(secret !in LocalPlaybackPolicy.availableAudioLabels(asset).single())
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
    fun shortVideosDoNotResetResumePositionAtStartOrMidPlayback() {
        assertFalse(LocalPlaybackPolicy.completedByPosition(0L, 20_000L))
        assertFalse(LocalPlaybackPolicy.completedByPosition(1_000L, 20_000L))
        assertFalse(LocalPlaybackPolicy.completedByPosition(10_000L, 20_000L))
        assertEquals(10_000L, LocalPlaybackPolicy.persistedPositionForStop(10_000L, 20_000L))
        assertTrue(LocalPlaybackPolicy.completedByPosition(15_000L, 20_000L))
        assertEquals(0L, LocalPlaybackPolicy.persistedPositionForStop(15_000L, 20_000L))
        assertEquals(0L, LocalPlaybackPolicy.persistedPositionForStop(20_000L, 20_000L))
    }

    @Test
    fun shortAndTinyDurationsRespectNearEndBoundary() {
        assertFalse(LocalPlaybackPolicy.completedByPosition(2_999L, 4_000L))
        assertTrue(LocalPlaybackPolicy.completedByPosition(3_000L, 4_000L))
        assertFalse(LocalPlaybackPolicy.completedByPosition(0L, 1L))
        assertTrue(LocalPlaybackPolicy.completedByPosition(1L, 1L))
        assertFalse(LocalPlaybackPolicy.completedByPosition(500L, 0L))
        assertFalse(LocalPlaybackPolicy.completedByPosition(-1L, 20_000L))
        assertTrue(LocalPlaybackPolicy.completedByPosition(95_000L, 120_000L))
    }

    @Test
    fun persistedPlaybackAssetsCarryLibraryIdentityWhenAvailable() {
        val asset = LocalPlaybackAsset(
            videoPath = "/library/video-1080p.mp4",
            title = "Fixture title",
            itemId = "item-1",
        )

        assertEquals("item-1", LocalPlaybackPolicy.persistableItemId(asset))
        assertEquals(null, LocalPlaybackPolicy.persistableItemId(asset.copy(itemId = "")))
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

    @Test
    fun subtitleSelectionCyclesEveryTrackThenOffThenFirstTrack() {
        assertEquals(-1, LocalPlaybackPolicy.nextSubtitleSelection(current = 0, trackCount = 0))
        assertEquals(0, LocalPlaybackPolicy.nextSubtitleSelection(current = -1, trackCount = 2))
        assertEquals(1, LocalPlaybackPolicy.nextSubtitleSelection(current = 0, trackCount = 2))
        assertEquals(-1, LocalPlaybackPolicy.nextSubtitleSelection(current = 1, trackCount = 2))
        assertEquals(0, LocalPlaybackPolicy.nextSubtitleSelection(current = -1, trackCount = 1))
        assertEquals(-1, LocalPlaybackPolicy.nextSubtitleSelection(current = 0, trackCount = 1))
    }

    @Test
    fun audioSelectionCyclesPersistedTracksAndWrapsWithoutOff() {
        assertEquals(-1, LocalPlaybackPolicy.nextAudioSelection(current = 0, trackCount = 0))
        assertEquals(0, LocalPlaybackPolicy.nextAudioSelection(current = -1, trackCount = 3))
        assertEquals(1, LocalPlaybackPolicy.nextAudioSelection(current = 0, trackCount = 3))
        assertEquals(2, LocalPlaybackPolicy.nextAudioSelection(current = 1, trackCount = 3))
        assertEquals(0, LocalPlaybackPolicy.nextAudioSelection(current = 2, trackCount = 3))
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
