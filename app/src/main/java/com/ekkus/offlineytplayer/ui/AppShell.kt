package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal enum class AppDestination(val label: String, val accessibilityLabel: String) {
    Library("Library", "Open offline library"), Downloads("Downloads", "Open downloads"),
    Add("Add", "Add a video"), Settings("Settings", "Open settings"),
}

internal object PortraitLayoutPolicy {
    const val BottomDestinationCount = 4
    const val CompactPortraitHeightDp = 640
    const val LargeFontScale = 1.30f
    const val PrimarySetupControlCount = 4
    fun primaryControlsFit(heightDp: Int, fontScale: Float): Boolean {
        val reservedChrome = 64 + 80
        val minimumContent = if (fontScale >= LargeFontScale) 180 else 160
        return heightDp - reservedChrome >= minimumContent
    }
}

@Composable
fun OfflineYTPlayerApp(initialSharedUrl: String? = null) {
    OfflineYTPlayerTheme {
        var destination by rememberSaveable(initialSharedUrl) { mutableStateOf(if (initialSharedUrl == null) AppDestination.Library else AppDestination.Add) }
        FixedRegionScaffold(destination.label, destination, { destination = it }) { padding -> DestinationContent(destination, padding, initialSharedUrl) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FixedRegionScaffold(title: String, destination: AppDestination, onDestinationSelected: (AppDestination) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(title) }) }, bottomBar = { NavigationBar {
            AppDestination.entries.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { onDestinationSelected(item) },
                icon = { Box(Modifier.sizeIn(minWidth = MidnightTransit.MinimumTouchTarget, minHeight = MidnightTransit.MinimumTouchTarget).semantics { contentDescription = item.accessibilityLabel }, contentAlignment = Alignment.Center) { Text(item.label.take(1)) } }, label = { Text(item.label) }) }
        } }, content = content)
}

@Composable
private fun DestinationContent(destination: AppDestination, padding: PaddingValues, initialSharedUrl: String?) {
    when (destination) {
        AppDestination.Add -> AddScreen(padding, initialSharedUrl)
        else -> Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(when (destination) {
                AppDestination.Library -> "No offline videos yet"; AppDestination.Downloads -> "No downloads yet"; AppDestination.Settings -> "Downloads · Playback · Storage · Appearance · About"; AppDestination.Add -> ""
            }, textAlign = TextAlign.Center) }
            if (destination == AppDestination.Library) Button(onClick = {}, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Add video") }
        }
    }
}

@Composable
private fun AddScreen(padding: PaddingValues, initialSharedUrl: String?) {
    var url by rememberSaveable(initialSharedUrl) { mutableStateOf(initialSharedUrl.orEmpty()) }
    var analyzed by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text("Download a supported video for offline playback.")
        OutlinedTextField(value = url, onValueChange = { url = it; analyzed = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Video URL") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Paste") }
            Button(onClick = { analyzed = url.isNotBlank() }, enabled = url.isNotBlank(), modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Analyze") }
        }
        Text("Supports recognized YouTube video URLs. Playlists and channel pages are not supported.")
        if (analyzed) DownloadSetupPreview()
    }
}

@Composable
private fun DownloadSetupPreview() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text("Download setup")
        Text("Video details will appear here after source resolution.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Options") }
            Button(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Download") }
        }
    }
}
