package com.ekkus.offlineytplayer.ui

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy

internal object PlayerLayoutPolicy {
    const val VideoAspectRatio = 16f / 9f
    const val PrimaryTransportActions = 3
    const val SecondaryControlActions = 3
    const val HasScrollingControls = false
    const val HasLandscapeAction = false
}

@Composable
internal fun PortraitPlayerScreen(asset: LocalPlaybackAsset, onBack: () -> Unit) {
    val validated = remember(asset) { LocalPlaybackPolicy.validate(asset) }
    val context = LocalContext.current
    val player = remember(validated.videoPath) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(LocalPlaybackPolicy.SkipIntervalMs)
            .setSeekForwardIncrementMs(LocalPlaybackPolicy.SkipIntervalMs)
            .build()
            .apply {
                setMediaItem(LocalPlaybackPolicy.mediaItemFor(validated.videoPath), validated.startPositionMs)
                prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Column(
        Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(validated.title)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Back") }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(PlayerLayoutPolicy.VideoAspectRatio),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    this.player = player
                }
            },
            update = { it.player = player },
        )
        Text("Offline local playback")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            OutlinedButton(
                onClick = { player.seekBack() },
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("-10s") }
            Button(
                onClick = { if (player.isPlaying) player.pause() else player.play() },
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Play/Pause") }
            OutlinedButton(
                onClick = { player.seekForward() },
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("+10s") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            SecondaryControl("Speed") { player.setPlaybackSpeed(nextSpeed(player.playbackParameters.speed)) }
            SecondaryControl("Subtitles") { }
            SecondaryControl("Audio") { }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SecondaryControl(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
    ) { Text(label) }
}

private fun nextSpeed(current: Float): Float = when {
    current < 1f -> 1f
    current < 1.25f -> 1.25f
    current < 1.5f -> 1.5f
    current < 2f -> 2f
    else -> 0.75f
}
