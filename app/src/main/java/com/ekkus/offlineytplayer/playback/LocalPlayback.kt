package com.ekkus.offlineytplayer.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

internal data class LocalSubtitleTrack(
    val path: String,
    val language: String,
    val label: String? = null,
    val mimeType: String,
)

internal data class LocalAudioTrack(
    val path: String,
    val language: String? = null,
    val label: String? = null,
)

internal data class LocalPlaybackAsset(
    val videoPath: String,
    val audioPath: String? = null,
    val title: String,
    val startPositionMs: Long = 0,
    val subtitleTracks: List<LocalSubtitleTrack> = emptyList(),
    val audioTracks: List<LocalAudioTrack> = emptyList(),
)

internal data class LocalPlaybackSourcePlan(
    val videoPath: String,
    val audioPath: String? = null,
    val startPositionMs: Long = 0,
    val subtitleTracks: List<LocalSubtitleTrack> = emptyList(),
    val audioTracks: List<LocalAudioTrack> = emptyList(),
) {
    val usesSeparateAudioVideoAssets: Boolean
        get() = audioPath != null

    val hasMultipleAudioTracks: Boolean
        get() = audioTracks.size > 1
}

internal data class LocalPlaybackRequest(
    val videoPath: String,
    val audioPath: String? = null,
    val startPositionMs: Long = 0,
    val subtitleTracks: List<LocalSubtitleTrack> = emptyList(),
    val audioTracks: List<LocalAudioTrack> = emptyList(),
)

internal object LocalPlaybackPolicy {
    const val SkipIntervalMs = 10_000L
    const val NearEndCompletedThresholdMs = 30_000L
    const val UsesNetworkUris = false
    const val SupportsLandscapeAction = false

    fun validate(asset: LocalPlaybackAsset): LocalPlaybackAsset {
        require(asset.videoPath.isNotBlank()) { "video path is required" }
        require(!looksRemote(asset.videoPath)) { "remote playback URIs are forbidden" }
        asset.audioPath?.let {
            require(it.isNotBlank()) { "audio path must be non-blank when present" }
            require(!looksRemote(it)) { "remote playback URIs are forbidden" }
            require(it != asset.videoPath) { "separate audio and video assets must use distinct paths" }
        }
        asset.subtitleTracks.forEach(::validateSubtitleTrack)
        asset.audioTracks.forEach(::validateAudioTrack)
        require(asset.startPositionMs >= 0) { "start position must be non-negative" }
        return asset
    }

    fun mediaSourcePlanFor(asset: LocalPlaybackAsset): LocalPlaybackSourcePlan {
        val validated = validate(asset)
        return LocalPlaybackSourcePlan(
            videoPath = validated.videoPath,
            audioPath = validated.audioPath,
            startPositionMs = validated.startPositionMs,
            subtitleTracks = validated.subtitleTracks,
            audioTracks = validated.audioTracks,
        )
    }

    fun playbackRequestFor(asset: LocalPlaybackAsset): LocalPlaybackRequest {
        val validated = validate(asset)
        return LocalPlaybackRequest(
            videoPath = validated.videoPath,
            audioPath = validated.audioPath,
            startPositionMs = validated.startPositionMs,
            subtitleTracks = validated.subtitleTracks,
            audioTracks = validated.audioTracks,
        )
    }

    fun mediaItemFor(asset: LocalPlaybackAsset): MediaItem {
        val request = playbackRequestFor(asset)
        val subtitleConfigurations = request.subtitleTracks.map(::subtitleConfigurationFor)
        return mediaItemBuilderFor(request.videoPath)
            .setSubtitleConfigurations(subtitleConfigurations)
            .setTag(request.audioPath)
            .build()
    }

    fun mediaItemFor(path: String): MediaItem {
        require(path.isNotBlank()) { "local path is required" }
        require(!looksRemote(path)) { "remote playback URIs are forbidden" }
        return mediaItemBuilderFor(path).build()
    }

    fun splitAudioPathFrom(item: MediaItem): String? = item.localConfiguration?.tag as? String

    fun availableSubtitleLabels(asset: LocalPlaybackAsset): List<String> = validate(asset).subtitleTracks.map { track ->
        track.label?.takeIf(String::isNotBlank) ?: track.language
    }

    fun availableAudioLabels(asset: LocalPlaybackAsset): List<String> = validate(asset).audioTracks.mapIndexed { index, track ->
        track.label?.takeIf(String::isNotBlank)
            ?: track.language?.takeIf(String::isNotBlank)
            ?: "Track ${index + 1}"
    }

    fun shouldEnableAudioSelection(asset: LocalPlaybackAsset): Boolean = availableAudioLabels(asset).size > 1

    @OptIn(UnstableApi::class)
    fun mediaSourceFor(
        asset: LocalPlaybackAsset,
        mediaSourceFactory: DefaultMediaSourceFactory,
    ): MediaSource {
        val plan = mediaSourcePlanFor(asset)
        val videoSource = mediaSourceFactory.createMediaSource(mediaItemFor(asset))
        val audioPath = plan.audioPath ?: return videoSource
        val audioSource = mediaSourceFactory.createMediaSource(mediaItemFor(audioPath))
        return MergingMediaSource(videoSource, audioSource)
    }

    fun completedByPosition(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return durationMs - positionMs.coerceAtMost(durationMs) <= NearEndCompletedThresholdMs
    }

    private fun validateSubtitleTrack(track: LocalSubtitleTrack) {
        require(track.path.isNotBlank()) { "subtitle path is required" }
        require(!looksRemote(track.path)) { "remote playback URIs are forbidden" }
        require(track.language.isNotBlank()) { "subtitle language is required" }
        require(track.mimeType in supportedSubtitleMimeTypes) { "unsupported subtitle mime type" }
    }

    private fun validateAudioTrack(track: LocalAudioTrack) {
        require(track.path.isNotBlank()) { "audio track path is required" }
        require(!looksRemote(track.path)) { "remote playback URIs are forbidden" }
    }

    private fun subtitleConfigurationFor(track: LocalSubtitleTrack): SubtitleConfiguration =
        SubtitleConfiguration.Builder(Uri.fromFile(File(track.path)))
            .setMimeType(track.mimeType)
            .setLanguage(track.language)
            .setLabel(track.label)
            .build()

    private fun mediaItemBuilderFor(path: String): MediaItem.Builder {
        require(path.isNotBlank()) { "local path is required" }
        require(!looksRemote(path)) { "remote playback URIs are forbidden" }
        return MediaItem.Builder().setUri(Uri.fromFile(File(path)))
    }

    private fun looksRemote(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }

    private val supportedSubtitleMimeTypes = setOf(
        MimeTypes.TEXT_VTT,
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.APPLICATION_TTML,
    )
}
