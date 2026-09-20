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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class AppDestination(val label: String, val accessibilityLabel: String) {
    Library("Library", "Open offline library"), Downloads("Downloads", "Open downloads"), Add("Add", "Add a video"), Settings("Settings", "Open settings")
}
internal enum class SettingsSection(val label: String) { Downloads("Downloads"), Playback("Playback"), Storage("Storage"), Appearance("Appearance"), About("About") }
internal object PortraitLayoutPolicy {
    const val BottomDestinationCount = 4; const val CompactPortraitHeightDp = 640; const val LargeFontScale = 1.30f; const val PrimarySetupControlCount = 4; const val AdvancedOptionsRowCount = 4; const val SettingsHubRowCount = 5; const val MaxSettingsRows = 5
    fun primaryControlsFit(heightDp: Int, fontScale: Float): Boolean { val reservedChrome = 144; val minimumContent = if (fontScale >= LargeFontScale) 180 else 160; return heightDp - reservedChrome >= minimumContent }
}

@Composable
internal fun OfflineYTPlayerApp(
    initialSharedUrl: String? = null,
    libraryState: LibraryScreenState = LibraryScreenState.Unavailable("Library repository is not connected yet; no empty-library claim is being made."),
    downloadsState: DownloadsScreenState = DownloadsScreenState.Unavailable("Downloads repository is not connected yet; no empty-queue claim is being made."),
    downloadControlGateway: AppDownloadControlGateway? = null,
    sourceAnalysisGateway: AppSourceAnalysisGateway? = null,
) {
    OfflineYTPlayerTheme {
        var destination by rememberSaveable(initialSharedUrl) { mutableStateOf(if (initialSharedUrl == null) AppDestination.Library else AppDestination.Add) }
        var playbackAsset by remember { mutableStateOf<LocalPlaybackAsset?>(null) }
        val activePlaybackAsset = playbackAsset
        if (activePlaybackAsset != null) PortraitPlayerScreen(activePlaybackAsset) { playbackAsset = null } else FixedRegionScaffold(destination.label, destination, { destination = it }) { padding ->
            DestinationContent(destination, padding, initialSharedUrl, libraryState, downloadsState, downloadControlGateway, sourceAnalysisGateway, { destination = AppDestination.Add }, { playbackAsset = it })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FixedRegionScaffold(title: String, destination: AppDestination, onDestinationSelected: (AppDestination) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Text(title) }) }, bottomBar = { NavigationBar { AppDestination.entries.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { onDestinationSelected(item) }, icon = { Box(Modifier.sizeIn(minWidth = MidnightTransit.MinimumTouchTarget, minHeight = MidnightTransit.MinimumTouchTarget).semantics { contentDescription = item.accessibilityLabel }, contentAlignment = Alignment.Center) { Text(item.label.take(1)) } }, label = { Text(item.label) }) } } }, content = content)
}

@Composable
private fun DestinationContent(destination: AppDestination, padding: PaddingValues, initialSharedUrl: String?, libraryState: LibraryScreenState, downloadsState: DownloadsScreenState, downloadControlGateway: AppDownloadControlGateway?, sourceAnalysisGateway: AppSourceAnalysisGateway?, onAdd: () -> Unit, onPlay: (LocalPlaybackAsset) -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding)) { when (destination) {
        AppDestination.Library -> LibraryScreen(onAdd, onPlay, libraryState)
        AppDestination.Downloads -> DownloadsScreen(downloadsState, downloadControlGateway)
        AppDestination.Add -> AddScreen(PaddingValues(), initialSharedUrl, sourceAnalysisGateway, downloadControlGateway)
        AppDestination.Settings -> SettingsScreen(PaddingValues())
    } }
}

@Composable private fun SettingsScreen(padding: PaddingValues) { var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }; if (section == null) SettingsHub(padding) { section = it } else SettingsPage(padding, section!!) { section = null } }
@Composable private fun SettingsHub(padding: PaddingValues, onOpen: (SettingsSection) -> Unit) { Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Text("Choose a settings category. Primary settings stay on dedicated fixed-layout pages."); SettingsSection.entries.forEach { section -> OutlinedButton(onClick = { onOpen(section) }, modifier = Modifier.fillMaxWidth().weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(section.label) } } } }
@Composable private fun SettingsPage(padding: PaddingValues, section: SettingsSection, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(section.label); OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Back") } }; when (section) { SettingsSection.Downloads -> { SettingValue("Default quality", "Best compatible"); SettingToggle("Wi-Fi only", true); SettingValue("Concurrent downloads", "2"); SettingValue("Subtitles", "Preferred language"); SettingValue("Retry", "Automatic") }; SettingsSection.Playback -> { SettingToggle("Remember position", true); SettingValue("Default speed", "1.0×"); SettingValue("Skip interval", "10 seconds"); SettingValue("Subtitles", "Remember selection"); SettingValue("Audio", "Default track") }; SettingsSection.Storage -> { SettingValue("Storage location", "App media directory"); SettingValue("Used / free", "Calculated on device"); SettingValue("Thumbnail cache", "Manage"); SettingValue("Incomplete files", "Clean up") }; SettingsSection.Appearance -> { SettingValue("Theme", "Dark · Light · System"); SettingValue("Default", "Dark"); SettingValue("Library layout", "List") }; SettingsSection.About -> { SettingValue("Version / build", "0.1.0"); SettingValue("Licenses", "Open-source notices"); SettingValue("Privacy", "Local-first"); SettingValue("Diagnostics", "Export when enabled"); SettingValue("Source-service notice", "Review before public release") } } } }
@Composable private fun SettingValue(label: String, value: String) { Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Text(value, textAlign = TextAlign.End) } }
@Composable private fun SettingToggle(label: String, initial: Boolean) { var checked by rememberSaveable(label) { mutableStateOf(initial) }; Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Switch(checked = checked, onCheckedChange = { checked = it }) } }

@Composable
private fun AddScreen(padding: PaddingValues, initialSharedUrl: String?, sourceGateway: AppSourceAnalysisGateway?, downloadControlGateway: AppDownloadControlGateway?) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable(initialSharedUrl) { mutableStateOf(initialSharedUrl.orEmpty()) }
    var setup by remember { mutableStateOf<DownloadSetupState?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var activeAnalysisUrl by remember { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    fun cancelSupersededAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        activeAnalysisUrl = null
        analyzing = false
    }
    DisposableEffect(Unit) { onDispose { analysisJob?.cancel() } }
    if (advanced) { AdvancedDownloadOptions(padding) { advanced = false }; return }
    Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text("Download a supported video for offline playback.")
        OutlinedTextField(value = url, onValueChange = { url = it; cancelSupersededAnalysis(); setup = null; status = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Video URL") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedButton(onClick = { val pasted = clipboard.getText()?.text?.takeIf { it.isNotBlank() }; if (pasted == null) status = "Clipboard does not contain a video URL." else { cancelSupersededAnalysis(); url = pasted.take(4096); setup = null; status = "Pasted clipboard text. Choose Analyze to validate it." } }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Paste") }
            Button(onClick = {
                val gateway = sourceGateway
                if (gateway == null) { status = "Source resolver is unavailable."; return@Button }
                val requestUrl = url
                analysisJob?.cancel()
                activeAnalysisUrl = requestUrl
                analyzing = true; setup = null; status = "Resolving source…"
                analysisJob = scope.launch {
                    val result = withContext(Dispatchers.IO) { gateway.analyze(requestUrl) }
                    if (activeAnalysisUrl != requestUrl) return@launch
                    analyzing = false
                    analysisJob = null
                    result.error?.let { status = it.message; return@launch }
                    val analysis = result.value ?: run { status = "Source resolver returned no media."; return@launch }
                    setup = DownloadSetupState(analysis.sourceUrl, analysis.title, analysis.durationMs?.let(::formatSetupDuration) ?: "Unknown duration", analysis.qualityLabel, analysis.estimatedBytes?.let(::formatSetupBytes) ?: "Size unavailable", true)
                    status = null
                }
            }, enabled = url.isNotBlank() && !analyzing && !scheduling, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(if (analyzing) "Analyzing…" else "Analyze") }
        }
        Text("Supports recognized YouTube video URLs. Playlists and channel pages are not supported.")
        status?.let { Text(it) }
        setup?.let { setupState ->
            DownloadSetupPreview(
                setupState,
                onOptions = { advanced = true },
                onDownload = {
                    val gateway = downloadControlGateway
                    if (gateway == null) { status = "Download scheduler is unavailable."; return@DownloadSetupPreview }
                    scheduling = true
                    status = "Scheduling download…"
                    val jobId = setupState.sourceUrl
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { gateway.enqueue(jobId) }
                        scheduling = false
                        result.error?.let { status = it.message; return@launch }
                        status = if (result.value == true) "Download scheduled." else "Download was not queued."
                    }
                },
            )
        }
    }
}

private fun formatSetupDuration(durationMs: Long): String { val seconds = durationMs / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
private fun formatSetupBytes(bytes: Long): String = if (bytes >= 1024L * 1024L) "%.1f MB".format(bytes.toDouble() / (1024.0 * 1024.0)) else "$bytes bytes"

@Composable private fun DownloadSetupPreview(setup: DownloadSetupState, onOptions: () -> Unit, onDownload: () -> Unit) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Text("Download setup"); Text(setup.title); Text("Source: ${setup.sourceUrl}"); Text("${setup.durationLabel} · ${setup.qualityLabel} · ${setup.estimatedSizeLabel}"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { OutlinedButton(onClick = onOptions, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Options") }; Button(onClick = onDownload, enabled = setup.readyForDownload, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Download") } } } }
@Composable private fun AdvancedDownloadOptions(padding: PaddingValues, onBack: () -> Unit) { var subtitles by rememberSaveable { mutableStateOf(true) }; Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Download options"); OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Back") } }; SettingValue("Audio track", "Default"); Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Download subtitles"); Switch(checked = subtitles, onCheckedChange = { subtitles = it }) }; SettingValue("Subtitle language", "Preferred"); SettingValue("Container strategy", "Best compatible"); Box(Modifier.weight(1f)); Button(onClick = onBack, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Apply options") } } }
