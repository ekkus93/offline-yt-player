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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiPlaybackPositionGateway
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.playback.PlaybackSessionService
import com.ekkus.offlineytplayer.settings.AppSettingsMutation
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal object PlayerLayoutPolicy {
    const val VideoAspectRatio = 16f / 9f
    const val PrimaryTransportActions = 3
    const val SecondaryControlActions = 3
    const val HasScrollingControls = false
    const val HasLandscapeAction = false
}

@Composable
internal fun PortraitPlayerScreen(
    asset: LocalPlaybackAsset,
    settings: AppSettingsSnapshot,
    onUpdateSettings: (AppSettingsMutation.() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val validated = remember(asset) { LocalPlaybackPolicy.validate(asset) }
    val context = LocalContext.current
    val subtitleLabels = remember(validated) { LocalPlaybackPolicy.availableSubtitleLabels(validated) }
    val audioLabels = remember(validated) { LocalPlaybackPolicy.availableAudioLabels(validated) }
    var selectedSubtitleIndex by remember(validated) { mutableIntStateOf(if (subtitleLabels.isEmpty()) -1 else 0) }
    var selectedAudioIndex by remember(validated) { mutableIntStateOf(if (audioLabels.isEmpty()) -1 else 0) }
    var controller by remember(asset) { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context, validated.videoPath, validated.audioPath, validated.subtitleTracks, validated.audioTracks, validated.startPositionMs, validated.itemId, settings.rememberPlaybackPosition) {
        val token = SessionToken(context, ComponentName(context, PlaybackSessionService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val mainHandler = Handler(Looper.getMainLooper())
        val mainExecutor = Executor { command -> mainHandler.post(command) }
        val persistenceExecutor = playbackPersistenceExecutor()
        val databasePath = java.io.File(context.filesDir, "offline-yt-player.sqlite3").absolutePath
        val persistableItemId = LocalPlaybackPolicy.persistableItemId(validated)
        val configuredStartPositionMs = if (settings.rememberPlaybackPosition) LocalPlaybackPolicy.restoredStartPosition(validated.startPositionMs) else 0L
        var acquiredController: MediaController? = null
        var disposed = false
        var lastSavedPositionMs = configuredStartPositionMs

        fun persistPlaybackPosition(finalTransition: Boolean) {
            if (!settings.rememberPlaybackPosition) return
            val itemId = persistableItemId ?: return
            val activeController = acquiredController ?: return
            val durationMs = activeController.knownDurationMs()
            val currentPositionMs = activeController.knownPositionMs()
            val persistedPositionMs = if (durationMs == null) currentPositionMs else LocalPlaybackPolicy.persistedPositionForStop(currentPositionMs, durationMs)
            if (!finalTransition && durationMs != null && !LocalPlaybackPolicy.shouldPersistPosition(lastSavedPositionMs, currentPositionMs, durationMs)) return
            lastSavedPositionMs = persistedPositionMs
            persistenceExecutor.execute { GeneratedUniffiPlaybackPositionGateway.open(databasePath).use { gateway -> gateway.savePlaybackPosition(itemId, persistedPositionMs, durationMs) } }
        }

        val periodicSaver = object : Runnable { override fun run() { if (disposed) return; persistPlaybackPosition(false); mainHandler.postDelayed(this, LocalPlaybackPolicy.PositionPersistCadenceMs) } }
        future.addListener({ if (!future.isCancelled) { runCatching { future.get() }.getOrNull()?.let { connected -> acquiredController = connected; connected.setMediaItem(LocalPlaybackPolicy.mediaItemFor(validated), configuredStartPositionMs); connected.setPlaybackSpeed(settings.playbackSpeed); connected.prepare(); controller = connected; if (settings.rememberPlaybackPosition) mainHandler.postDelayed(periodicSaver, LocalPlaybackPolicy.PositionPersistCadenceMs) } } }, mainExecutor)
        onDispose { disposed = true; mainHandler.removeCallbacks(periodicSaver); persistPlaybackPosition(true); future.cancel(true); if (controller === acquiredController) controller = null; acquiredController?.release(); persistenceExecutor.shutdown() }
    }

    Column(Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(validated.title); OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Back") } }
        AndroidView(modifier = Modifier.fillMaxWidth().aspectRatio(PlayerLayoutPolicy.VideoAspectRatio), factory = { viewContext -> PlayerView(viewContext).apply { layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT); useController = false; player = controller } }, update = { it.player = controller })
        Text(if (controller == null) "Connecting to playback session…" else "Offline local playback")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedButton(onClick = { controller?.seekBack() }, enabled = controller != null, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("-10s") }
            Button(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }, enabled = controller != null, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Play/Pause") }
            OutlinedButton(onClick = { controller?.seekForward() }, enabled = controller != null, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("+10s") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            SecondaryControl("Speed ${settings.playbackSpeed}×", enabled = controller != null) { val speed = nextSpeed(settings.playbackSpeed); controller?.setPlaybackSpeed(speed); onUpdateSettings { playbackSpeed = speed } }
            SecondaryControl(subtitleControlLabel(subtitleLabels, selectedSubtitleIndex), controller != null && subtitleLabels.isNotEmpty()) { selectedSubtitleIndex = (selectedSubtitleIndex + 1) % subtitleLabels.size }
            SecondaryControl(audioControlLabel(audioLabels, selectedAudioIndex), controller != null && LocalPlaybackPolicy.shouldEnableAudioSelection(validated)) { selectedAudioIndex = (selectedAudioIndex + 1) % audioLabels.size }
        }
    }
}

@Composable private fun androidx.compose.foundation.layout.RowScope.SecondaryControl(label: String, enabled: Boolean = true, onClick: () -> Unit) { OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(label) } }
private fun subtitleControlLabel(labels: List<String>, selectedIndex: Int): String = when { labels.isEmpty() -> "Subtitles"; selectedIndex in labels.indices -> "Subtitles: ${labels[selectedIndex]}"; else -> "Subtitles" }
private fun audioControlLabel(labels: List<String>, selectedIndex: Int): String = when { labels.isEmpty() -> "Audio"; selectedIndex in labels.indices -> "Audio: ${labels[selectedIndex]}"; else -> "Audio" }
private fun nextSpeed(current: Float): Float = when { current < 1f -> 1f; current < 1.25f -> 1.25f; current < 1.5f -> 1.5f; current < 2f -> 2f; else -> 0.75f }
private fun playbackPersistenceExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "offline-yt-playback-position").apply { isDaemon = true } }
private fun MediaController.knownPositionMs(): Long = currentPosition.coerceAtLeast(0L)
private fun MediaController.knownDurationMs(): Long? = duration.takeIf { value -> value > 0L && value != C.TIME_UNSET }
