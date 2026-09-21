package com.ekkus.offlineytplayer.ui

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.playback.PlaybackSessionService
import java.util.concurrent.Executor

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
    var controller by remember(asset) { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context, validated.videoPath, validated.startPositionMs) {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val mainHandler = Handler(Looper.getMainLooper())
        val mainExecutor = Executor { command -> mainHandler.post(command) }
        var acquiredController: MediaController? = null
        future.addListener(
            {
                if (!future.isCancelled) {
                    runCatching { future.get() }.getOrNull()?.let { connected ->
                        acquiredController = connected
                        connected.setMediaItem(
                            LocalPlaybackPolicy.mediaItemFor(validated.videoPath),
                            validated.startPositionMs,
                        )
                        connected.prepare()
                        controller = connected
                    }
                }
            },
            mainExecutor,
        )
        onDispose {
            future.cancel(true)
            if (controller === acquiredController) controller = null
            acquiredController?.release()
        }
    }

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
                    player = controller
                }
            },
            update = { it.player = controller },
        )
        Text(if (controller == null) "Connecting to playback session…" else "Offline local playback")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            OutlinedButton(
                onClick = { controller?.seekBack() },
                enabled = controller != null,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("-10s") }
            Button(
                onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
                enabled = controller != null,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Play/Pause") }
            OutlinedButton(
                onClick = { controller?.seekForward() },
                enabled = controller != null,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("+10s") }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            SecondaryControl("Speed", enabled = controller != null) {
                controller?.let { it.setPlaybackSpeed(nextSpeed(it.playbackParameters.speed)) }
            }
            SecondaryControl("Subtitles", enabled = false) { }
            SecondaryControl("Audio", enabled = false) { }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SecondaryControl(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
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
