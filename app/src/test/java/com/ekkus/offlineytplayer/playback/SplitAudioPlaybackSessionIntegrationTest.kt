package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitAudioPlaybackSessionIntegrationTest {
    @Test
    fun playbackSessionPlayerBuildsMergedMediaSourceForSplitAudioAssets() {
        val service = File("src/main/java/com/ekkus/offlineytplayer/playback/PlaybackSessionService.kt").readText()

        assertTrue(service.contains("setMediaSourceFactory("))
        assertTrue(service.contains("SplitAudioMediaSourceFactory(DefaultMediaSourceFactory(this))"))
        assertTrue(service.contains("LocalPlaybackPolicy.splitAudioPathFrom(mediaItem)"))
        assertTrue(service.contains("LocalPlaybackPolicy.mediaItemFor(audioPath)"))
        assertTrue(service.contains("MergingMediaSource(videoSource, audioSource)"))
        assertTrue(service.contains("setDrmSessionManagerProvider("))
        assertTrue(service.contains("setLoadErrorHandlingPolicy("))
    }

    @Test
    fun localPlaybackMediaItemCarriesSplitAudioPathToTheSessionFactory() {
        val localPlayback = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()

        assertTrue(localPlayback.contains(".setTag(request.audioPath)"))
        assertTrue(localPlayback.contains("fun splitAudioPathFrom(item: MediaItem): String?"))
        assertTrue(localPlayback.contains("item.localConfiguration?.tag as? String"))
        assertTrue(localPlayback.contains("require(!looksRemote(it)) { \"remote playback URIs are forbidden\" }"))
    }
}
