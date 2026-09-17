package com.ekkus.offlineytplayer.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

internal class LocalPlayerController(
    private val context: Context,
    private val mediaSourceFactory: DefaultMediaSourceFactory = DefaultMediaSourceFactory(context),
) {
    @OptIn(UnstableApi::class)
    fun createPreparedPlayer(asset: LocalPlaybackAsset): ExoPlayer {
        val plan = LocalPlaybackPolicy.mediaSourcePlanFor(asset)
        return ExoPlayer.Builder(context).build().also { player ->
            player.setMediaSource(LocalPlaybackPolicy.mediaSourceFor(asset, mediaSourceFactory))
            if (plan.startPositionMs > 0L) {
                player.seekTo(plan.startPositionMs)
            }
            player.prepare()
        }
    }
}
