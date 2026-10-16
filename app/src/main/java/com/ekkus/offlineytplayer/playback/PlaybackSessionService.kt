package com.ekkus.offlineytplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackSessionService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(SplitAudioMediaSourceFactory(DefaultMediaSourceFactory(this)))
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
            }
        mediaSession = MediaSession.Builder(this, player).build()
        playerCreationObserver?.invoke(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.let { session ->
            session.player.release()
            teardownObserver?.onTeardownStep(PlaybackSessionTeardownStep.PLAYER_RELEASED)
            session.release()
            teardownObserver?.onTeardownStep(PlaybackSessionTeardownStep.SESSION_RELEASED)
        }
        mediaSession = null
        super.onDestroy()
    }

    internal companion object {
        /** Test seam: null in production. Notified from [onDestroy] after each real release call. */
        @Volatile
        internal var teardownObserver: PlaybackSessionTeardownObserver? = null

        /** Test seam: null in production. Exposes the configured runtime player to instrumentation. */
        @Volatile
        internal var playerCreationObserver: ((ExoPlayer) -> Unit)? = null
    }
}

internal enum class PlaybackSessionTeardownStep { PLAYER_RELEASED, SESSION_RELEASED }

internal fun interface PlaybackSessionTeardownObserver {
    fun onTeardownStep(step: PlaybackSessionTeardownStep)
}

@OptIn(UnstableApi::class)
private class SplitAudioMediaSourceFactory(
    private val delegate: MediaSource.Factory,
) : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val videoSource = delegate.createMediaSource(mediaItem)
        val audioPath = LocalPlaybackPolicy.splitAudioPathFrom(mediaItem) ?: return videoSource
        val audioSource = delegate.createMediaSource(LocalPlaybackPolicy.mediaItemFor(audioPath))
        return MergingMediaSource(videoSource, audioSource)
    }

    override fun getSupportedTypes(): IntArray = delegate.getSupportedTypes()

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }
}
