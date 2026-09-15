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
import androidx.compose.material3.Switch
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

internal enum class AppDestination(val label: String, val accessibilityLabel: String) {
    Library("Library", "Open offline library"), Downloads("Downloads", "Open downloads"),
    Add("Add", "Add a video"), Settings("Settings", "Open settings"),
}

internal enum class SettingsSection(val label: String) {
    Downloads("Downloads"), Playback("Playback"), Storage("Storage"), Appearance("Appearance"), About("About")
}

internal object PortraitLayoutPolicy {
    const val BottomDestinationCount = 4
    const val CompactPortraitHeightDp = 640
    const val LargeFontScale = 1.30f
    const val PrimarySetupControlCount = 4
    const val AdvancedOptionsRowCount = 4
    const val SettingsHubRowCount = 5
    const val MaxSettingsRows = 5
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
        FixedRegionScaffold(destination.label, destination, { destination = it }) { padding ->
            DestinationContent(destination, padding, initialSharedUrl) { destination = AppDestination.Add }
        }
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
private fun DestinationContent(destination: AppDestination, padding: PaddingValues, initialSharedUrl: String?, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        when (destination) {
            AppDestination.Library -> LibraryScreen(onAdd)
            AppDestination.Downloads -> DownloadsScreen()
            AppDestination.Add -> AddScreen(PaddingValues(), initialSharedUrl)
            AppDestination.Settings -> SettingsScreen(PaddingValues())
        }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues) {
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    if (section == null) SettingsHub(padding) { section = it } else SettingsPage(padding, section!!) { section = null }
}

@Composable
private fun SettingsHub(padding: PaddingValues, onOpen: (SettingsSection) -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text("Choose a settings category. Primary settings stay on dedicated fixed-layout pages.")
        SettingsSection.entries.forEach { section ->
            OutlinedButton(onClick = { onOpen(section) }, modifier = Modifier.fillMaxWidth().weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(section.label) }
        }
    }
}

@Composable
private fun SettingsPage(padding: PaddingValues, section: SettingsSection, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(section.label)
            OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Back") }
        }
        when (section) {
            SettingsSection.Downloads -> { SettingValue("Default quality", "Best compatible"); SettingToggle("Wi-Fi only", true); SettingValue("Concurrent downloads", "2"); SettingValue("Subtitles", "Preferred language"); SettingValue("Retry", "Automatic") }
            SettingsSection.Playback -> { SettingToggle("Remember position", true); SettingValue("Default speed", "1.0×"); SettingValue("Skip interval", "10 seconds"); SettingValue("Subtitles", "Remember selection"); SettingValue("Audio", "Default track") }
            SettingsSection.Storage -> { SettingValue("Storage location", "App media directory"); SettingValue("Used / free", "Calculated on device"); SettingValue("Thumbnail cache", "Manage"); SettingValue("Incomplete files", "Clean up") }
            SettingsSection.Appearance -> { SettingValue("Theme", "Dark · Light · System"); SettingValue("Default", "Dark"); SettingValue("Library layout", "List") }
            SettingsSection.About -> { SettingValue("Version / build", "0.1.0"); SettingValue("Licenses", "Open-source notices"); SettingValue("Privacy", "Local-first"); SettingValue("Diagnostics", "Export when enabled"); SettingValue("Source-service notice", "Review before public release") }
        }
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Text(value, textAlign = TextAlign.End) }
}

@Composable
private fun SettingToggle(label: String, initial: Boolean) {
    var checked by rememberSaveable(label) { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Switch(checked = checked, onCheckedChange = { checked = it }) }
}

@Composable
private fun AddScreen(padding: PaddingValues, initialSharedUrl: String?) {
    var url by rememberSaveable(initialSharedUrl) { mutableStateOf(initialSharedUrl.orEmpty()) }
    var analyzed by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    if (advanced) { AdvancedDownloadOptions(padding) { advanced = false }; return }
    Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text("Download a supported video for offline playback.")
        OutlinedTextField(value = url, onValueChange = { url = it; analyzed = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Video URL") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Paste") }
            Button(onClick = { analyzed = url.isNotBlank() }, enabled = url.isNotBlank(), modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Analyze") }
        }
        Text("Supports recognized YouTube video URLs. Playlists and channel pages are not supported.")
        if (analyzed) DownloadSetupPreview { advanced = true }
    }
}

@Composable
private fun DownloadSetupPreview(onOptions: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text("Download setup"); Text("Video details will appear here after source resolution.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedButton(onClick = onOptions, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Options") }
            Button(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Download") }
        }
    }
}

@Composable
private fun AdvancedDownloadOptions(padding: PaddingValues, onBack: () -> Unit) {
    var subtitles by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Download options"); OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Back") } }
        SettingValue("Audio track", "Default")
        Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Download subtitles"); Switch(checked = subtitles, onCheckedChange = { subtitles = it }) }
        SettingValue("Subtitle language", "Preferred"); SettingValue("Container strategy", "Best compatible"); Box(Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Apply options") }
    }
}
