package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class PlayerUiState(
    val title: String,
    val positionText: String = "0:00",
    val durationText: String = "0:00",
    val speedLabel: String = "1x",
    val subtitlesLabel: String = "Subtitles",
    val audioLabel: String = "Audio",
)

internal object PlayerScreenLayoutPolicy {
    const val VideoAspectRatioWidth = 16
    const val VideoAspectRatioHeight = 9
    const val SupportsLandscapeAction = false
    const val RequiresPrimaryControlScrolling = false
    const val PrimaryTransportControlCount = 3
    const val SecondaryControlCount = 3
    const val CompactPortraitHeightDp = 640
    const val LargeFontScale = 1.3f

    val transportLabels = listOf("Skip back", "Play/Pause", "Skip forward")
    val secondaryLabels = listOf("Speed", "Subtitles", "Audio")

    fun videoAspectRatio(): Float = VideoAspectRatioWidth.toFloat() / VideoAspectRatioHeight.toFloat()

    fun primaryControlsFit(heightDp: Int, fontScale: Float): Boolean {
        val videoHeight = (heightDp * VideoAspectRatioHeight) / (VideoAspectRatioWidth * 2)
        val chromeBudget = 72
        val titleTimelineBudget = (112 * fontScale).toInt()
        val transportBudget = (52 * fontScale).toInt()
        val secondaryBudget = (52 * fontScale).toInt()
        val paddingBudget = 48
        return videoHeight + chromeBudget + titleTimelineBudget + transportBudget + secondaryBudget + paddingBudget <= heightDp
    }
}

@Composable
internal fun PlayerScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onSkipBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeed: () -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Player",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            OutlinedButton(onClick = onBack) { Text("Library") }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PlayerScreenLayoutPolicy.videoAspectRatio())
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Offline video",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = state.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.positionText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.durationText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            Button(
                modifier = Modifier.weight(1f).heightIn(min = MidnightTransit.MinimumTouchTarget),
                onClick = onSkipBack,
            ) { Text("-10s") }
            Button(
                modifier = Modifier.weight(1f).heightIn(min = MidnightTransit.MinimumTouchTarget),
                onClick = onPlayPause,
            ) { Text("Play") }
            Button(
                modifier = Modifier.weight(1f).heightIn(min = MidnightTransit.MinimumTouchTarget),
                onClick = onSkipForward,
            ) { Text("+10s") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f).heightIn(min = MidnightTransit.MinimumTouchTarget),
                onClick = onSpeed,
            ) { Text(state.speedLabel) }
            OutlinedButton(
                modifier = Modifier.weight(1f).heightIn(min = MidnightTransit.MinimumTouchTarget),
                onClick = onSubtitles,
            ) { Text(state.subtitlesLabel) }
            OutlinedButton(
                modifier = Modifier.weight(1f).heightIn(min = MidnightTransit.MinimumTouchTarget),
                onClick = onAudio,
            ) { Text(state.audioLabel) }
        }
    }
}
