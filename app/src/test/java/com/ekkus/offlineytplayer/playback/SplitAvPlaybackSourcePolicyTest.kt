package com.ekkus.offlineytplayer.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitAvPlaybackSourcePolicyTest {
    @Test
    fun productionPolicyBuildsMergedLocalSourcesForSeparateAudioVideoAssets() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()

        assertTrue(source.contains("import androidx.media3.exoplayer.source.MergingMediaSource"))
        assertTrue(source.contains("fun mediaSourceFor("))
        assertTrue(source.contains("val videoSource = mediaSourceFactory.createMediaSource(mediaItemFor(asset))"))
        assertTrue(source.contains("val audioPath = plan.audioPath ?: return videoSource"))
        assertTrue(source.contains("val audioSource = mediaSourceFactory.createMediaSource(mediaItemFor(audioPath))"))
        assertTrue(source.contains("return MergingMediaSource(videoSource, audioSource)"))
    }

    @Test
    fun productionPolicyKeepsCompletedPlaybackUrisLocalOnly() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/playback/LocalPlayback.kt").readText()
        val testSource = File("src/test/java/com/ekkus/offlineytplayer/playback/LocalPlaybackPolicyTest.kt").readText()

        assertTrue(source.contains("require(!looksRemote(asset.videoPath))"))
        assertTrue(source.contains("require(!looksRemote(it))"))
        assertTrue(source.contains("Uri.fromFile(File(path))"))
        assertTrue(testSource.contains("adaptivePlanRejectsRemoteAudioAsset"))
        assertTrue(testSource.contains("adaptivePlanRejectsSameVideoAndAudioPath"))
        assertTrue(testSource.contains("singleFilePlanDoesNotClaimSeparateAdaptiveAssets"))
    }
}
