package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class AppDestination(val label: String, val accessibilityLabel: String) {
    Library("Library", "Open offline library"),
    Downloads("Downloads", "Open downloads"),
    Add("Add", "Add a video"),
    Settings("Settings", "Open settings"),
}

internal object PortraitLayoutPolicy {
    const val BottomDestinationCount = 4
    const val CompactPortraitHeightDp = 640
    const val LargeFontScale = 1.30f

    fun primaryControlsFit(heightDp: Int, fontScale: Float): Boolean {
        val reservedChrome = 64 + 80
        val minimumContent = if (fontScale >= LargeFontScale) 180 else 160
        return heightDp - reservedChrome >= minimumContent
    }
}

@Composable
fun OfflineYTPlayerApp() {
    OfflineYTPlayerTheme {
        var destination by rememberSaveable { mutableStateOf(AppDestination.Library) }
        FixedRegionScaffold(
            title = destination.label,
            destination = destination,
            onDestinationSelected = { destination = it },
        ) { contentPadding ->
            DestinationContent(destination, contentPadding)
        }
    }
}

@Composable
internal fun FixedRegionScaffold(
    title: String,
    destination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(title) }) },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { onDestinationSelected(item) },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .sizeIn(
                                        minWidth = MidnightTransit.MinimumTouchTarget,
                                        minHeight = MidnightTransit.MinimumTouchTarget,
                                    )
                                    .semantics { contentDescription = item.accessibilityLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(item.label.take(1))
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
private fun DestinationContent(destination: AppDestination, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when (destination) {
                    AppDestination.Library -> "No offline videos yet"
                    AppDestination.Downloads -> "No downloads yet"
                    AppDestination.Add -> "Paste or share a supported video URL"
                    AppDestination.Settings -> "Downloads · Playback · Storage · Appearance · About"
                },
                textAlign = TextAlign.Center,
            )
        }
        if (destination == AppDestination.Library || destination == AppDestination.Add) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
                onClick = { },
            ) {
                Text(if (destination == AppDestination.Library) "Add video" else "Analyze")
            }
        }
    }
}
