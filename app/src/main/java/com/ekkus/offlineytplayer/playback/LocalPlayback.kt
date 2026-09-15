package com.ekkus.offlineytplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import java.io.File

internal data class LocalPlaybackAsset(
    val videoPath: String,
    val audioPath: String? = null,
    val title: String,
    val startPositionMs: Long = 0,
)

internal object LocalPlaybackPolicy {
    const val SkipIntervalMs = 10_000L
    const val NearEndCompletedThresholdMs = 30_000L
    const val UsesNetworkUris = false
    const val SupportsLandscapeAction = false

    fun validate(asset: LocalPlaybackAsset): LocalPlaybackAsset {
        require(asset.videoPath.isNotBlank()) { "video path is required" }
        require(!looksRemote(asset.videoPath)) { "remote playback URIs are forbidden" }
        asset.audioPath?.let { require(!looksRemote(it)) { "remote playback URIs are forbidden" } }
        require(asset.startPositionMs >= 0) { "start position must be non-negative" }
        return asset
    }

    fun mediaItemFor(path: String): MediaItem {
        require(path.isNotBlank()) { "local path is required" }
        require(!looksRemote(path)) { "remote playback URIs are forbidden" }
        return MediaItem.fromUri(Uri.fromFile(File(path)))
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
