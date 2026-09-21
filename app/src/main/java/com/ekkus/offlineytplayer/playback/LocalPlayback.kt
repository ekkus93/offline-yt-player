package com.ekkus.offlineytplayer.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

internal data class LocalPlaybackAsset(
    val videoPath: String,
    val audioPath: String? = null,
    val title: String,
    val startPositionMs: Long = 0,
)

internal data class LocalPlaybackSourcePlan(
    val videoPath: String,
    val audioPath: String? = null,
    val startPositionMs: Long = 0,
) {
    val usesSeparateAudioVideoAssets: Boolean
        get() = audioPath != null
}

internal object LocalPlaybackPolicy {
    const val SkipIntervalMs = 10_000L
    const val NearEndCompletedThresholdMs = 30_000L
    const val UsesNetworkUris = false
    const val SupportsLandscapeAction = false
    const val ExtraSplitAudioPath = "com.ekkus.offlineytplayer.extra.SPLIT_AUDIO_PATH"

    fun validate(asset: LocalPlaybackAsset): LocalPlaybackAsset {
        require(asset.videoPath.isNotBlank()) { "video path is required" }
        require(!looksRemote(asset.videoPath)) { "remote playback URIs are forbidden" }
        asset.audioPath?.let {
            require(it.isNotBlank()) { "audio path must be non-blank when present" }
            require(!looksRemote(it)) { "remote playback URIs are forbidden" }
            require(it != asset.videoPath) { "separate audio and video assets must use distinct paths" }
        }
        require(asset.startPositionMs >= 0) { "start position must be non-negative" }
        return asset
    }

    fun mediaSourcePlanFor(asset: LocalPlaybackAsset): LocalPlaybackSourcePlan {
        val validated = validate(asset)
        return LocalPlaybackSourcePlan(
            videoPath = validated.videoPath,
            audioPath = validated.audioPath,
            startPositionMs = validated.startPositionMs,
        )
    }

    fun mediaItemFor(asset: LocalPlaybackAsset): MediaItem {
        val validated = validate(asset)
        val builder = mediaItemBuilderFor(validated.videoPath)
        validated.audioPath?.let { audioPath ->
            builder.setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setExtras(Bundle().apply { putString(ExtraSplitAudioPath, audioPath) })
                    .build(),
            )
        }
        return builder.build()
    }

    fun mediaItemFor(path: String): MediaItem {
        require(path.isNotBlank()) { "local path is required" }
        require(!looksRemote(path)) { "remote playback URIs are forbidden" }
        return mediaItemBuilderFor(path).build()
    }

    fun splitAudioPathFrom(item: MediaItem): String? =
        item.requestMetadata.extras?.getString(ExtraSplitAudioPath)

    @OptIn(UnstableApi::class)
    fun mediaSourceFor(
        asset: LocalPlaybackAsset,
        mediaSourceFactory: DefaultMediaSourceFactory,
    ): MediaSource {
        val plan = mediaSourcePlanFor(asset)
        val videoSource = mediaSourceFactory.createMediaSource(mediaItemFor(plan.videoPath))
        val audioPath = plan.audioPath ?: return videoSource
        val audioSource = mediaSourceFactory.createMediaSource(mediaItemFor(audioPath))
        return MergingMediaSource(videoSource, audioSource)
    }

    fun completedByPosition(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return durationMs - positionMs.coerceAtMost(durationMs) <= NearEndCompletedThresholdMs
    }

    private fun mediaItemBuilderFor(path: String): MediaItem.Builder {
        require(path.isNotBlank()) { "local path is required" }
        require(!looksRemote(path)) { "remote playback URIs are forbidden" }
        return MediaItem.Builder().setUri(Uri.fromFile(File(path)))
    }

    private fun looksRemote(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }
}
