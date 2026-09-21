package com.ekkus.offlineytplayer.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

internal data class LocalSubtitleAsset(
    val path: String,
    val mimeType: String,
    val language: String,
    val label: String? = null,
)

internal data class LocalPlaybackAsset(
    val videoPath: String,
    val audioPath: String? = null,
    val title: String,
    val startPositionMs: Long = 0,
    val subtitles: List<LocalSubtitleAsset> = emptyList(),
)

internal data class LocalPlaybackSourcePlan(
    val videoPath: String,
    val audioPath: String? = null,
    val startPositionMs: Long = 0,
    val subtitles: List<LocalSubtitleAsset> = emptyList(),
) {
    val usesSeparateAudioVideoAssets: Boolean
        get() = audioPath != null
}

internal object LocalPlaybackPolicy {
    const val SkipIntervalMs = 10_000L
    const val NearEndCompletedThresholdMs = 30_000L
    const val UsesNetworkUris = false
    const val SupportsLandscapeAction = false
    private val supportedSubtitleMimeTypes = setOf("text/vtt", "application/x-subrip")

    fun validate(asset: LocalPlaybackAsset): LocalPlaybackAsset {
        require(asset.videoPath.isNotBlank()) { "video path is required" }
        require(!looksRemote(asset.videoPath)) { "remote playback URIs are forbidden" }
        asset.audioPath?.let {
            require(it.isNotBlank()) { "audio path must be non-blank when present" }
            require(!looksRemote(it)) { "remote playback URIs are forbidden" }
            require(it != asset.videoPath) { "separate audio and video assets must use distinct paths" }
        }
        asset.subtitles.forEach { subtitle ->
            require(subtitle.path.isNotBlank()) { "subtitle path is required" }
            require(!looksRemote(subtitle.path)) { "remote playback URIs are forbidden" }
            require(subtitle.mimeType in supportedSubtitleMimeTypes) { "unsupported subtitle MIME type" }
            require(subtitle.language.isNotBlank()) { "subtitle language is required" }
            require(subtitle.path != asset.videoPath && subtitle.path != asset.audioPath) {
                "subtitle asset must use a distinct path"
            }
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
            subtitles = validated.subtitles,
        )
    }

    fun mediaItemFor(path: String): MediaItem {
        require(path.isNotBlank()) { "local path is required" }
        require(!looksRemote(path)) { "remote playback URIs are forbidden" }
        return MediaItem.fromUri(Uri.fromFile(File(path)))
    }

    private fun mediaItemForVideo(plan: LocalPlaybackSourcePlan): MediaItem {
        val subtitles = plan.subtitles.map { subtitle ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(File(subtitle.path)))
                .setMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setLabel(subtitle.label)
                .build()
        }
        return MediaItem.Builder()
            .setUri(Uri.fromFile(File(plan.videoPath)))
            .setSubtitleConfigurations(subtitles)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun mediaSourceFor(
        asset: LocalPlaybackAsset,
        mediaSourceFactory: DefaultMediaSourceFactory,
    ): MediaSource {
        val plan = mediaSourcePlanFor(asset)
        val videoSource = mediaSourceFactory.createMediaSource(mediaItemForVideo(plan))
        val audioPath = plan.audioPath ?: return videoSource
        val audioSource = mediaSourceFactory.createMediaSource(mediaItemFor(audioPath))
        return MergingMediaSource(videoSource, audioSource)
    }

    fun completedByPosition(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return durationMs - positionMs.coerceAtMost(durationMs) <= NearEndCompletedThresholdMs
    }

    private fun looksRemote(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }
}
