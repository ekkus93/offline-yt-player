package com.ekkus.offlineytplayer.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryDetailsGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryMutationGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.coregateway.SourceAnalysisState
import com.ekkus.offlineytplayer.coregateway.SourceAnalysisUseCase
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.settings.AppSettingsMutation
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.AppearanceSetting
import com.ekkus.offlineytplayer.settings.LibraryLayoutSetting
import com.ekkus.offlineytplayer.settings.ManagedCleanup
import com.ekkus.offlineytplayer.settings.StorageSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal enum class AppDestination(val label: String, val accessibilityLabel: String) { Library("Library", "Open offline library"), Downloads("Downloads", "Open downloads"), Add("Add", "Add a video"), Settings("Settings", "Open settings") }
internal enum class SettingsSection(val label: String) { Downloads("Downloads"), Playback("Playback"), Storage("Storage"), Appearance("Appearance"), About("About") }
internal object PortraitLayoutPolicy { const val BottomDestinationCount = 4; const val CompactPortraitHeightDp = 640; const val LargeFontScale = 1.30f; const val PrimarySetupControlCount = 4; const val AdvancedOptionsRowCount = 4; const val SettingsHubRowCount = 5; const val MaxSettingsRows = 5; fun primaryControlsFit(heightDp: Int, fontScale: Float): Boolean { val reservedChrome = 144; val minimumContent = if (fontScale >= LargeFontScale) 180 else 160; return heightDp - reservedChrome >= minimumContent } }

@Composable
internal fun OfflineYTPlayerApp(
    initialSharedUrl: String? = null,
    libraryState: LibraryScreenState = LibraryScreenState.Unavailable(
        "Library repository is not connected yet; no empty-library claim is being made.",
    ),
    downloadsState: DownloadsScreenState = DownloadsScreenState.Unavailable(
        "Downloads repository is not connected yet; no empty-queue claim is being made.",
    ),
    downloadControlGateway: AppDownloadControlGateway? = null,
    sourceAnalysisGateway: AppSourceAnalysisGateway? = null,
    libraryDetailsGatewayProvider: () -> AppLibraryDetailsGateway? = { null },
    libraryMutationGatewayProvider: () -> AppLibraryMutationGateway? = { null },
    libraryRootPath: String? = null,
    onLibraryQueryChanged: (String) -> Unit = {},
    settingsSnapshot: AppSettingsSnapshot = AppSettingsSnapshot(),
    onUpdateSettings: (AppSettingsMutation.() -> Unit) -> Unit = {},
) {
    OfflineYTPlayerTheme(settingsSnapshot.appearance) {
        var destination by rememberSaveable(initialSharedUrl) {
            mutableStateOf(if (initialSharedUrl == null) AppDestination.Library else AppDestination.Add)
        }
        // Bottom destinations are real navigation transitions: Android Back must
        // restore the prior destination, including when launched from ACTION_SEND.
        // Store only stable destination names to survive activity recreation.
        var backStack by rememberSaveable(initialSharedUrl) {
            mutableStateOf(arrayListOf<String>())
        }
        val destinationStateHolder = rememberSaveableStateHolder()
        var playbackAsset by remember { mutableStateOf<LocalPlaybackAsset?>(null) }

        fun navigate(next: AppDestination) {
            if (destination == next) return
            backStack = ArrayList((backStack + destination.name).takeLast(32))
            destination = next
        }

        BackHandler(enabled = playbackAsset != null || backStack.isNotEmpty()) {
            if (playbackAsset != null) {
                playbackAsset = null
            } else {
                val previous = backStack.lastOrNull()
                backStack = ArrayList(backStack.dropLast(1))
                destination = AppDestination.entries.firstOrNull { it.name == previous }
                    ?: AppDestination.Library
            }
        }

        val activePlaybackAsset = playbackAsset
        if (activePlaybackAsset != null) {
            PortraitPlayerScreen(activePlaybackAsset, settingsSnapshot, onUpdateSettings) {
                playbackAsset = null
            }
        } else {
            FixedRegionScaffold(destination.label, destination, ::navigate) { padding ->
                destinationStateHolder.SaveableStateProvider(destination.name) {
                    DestinationContent(
                        destination, padding, initialSharedUrl, libraryState, downloadsState,
                        downloadControlGateway, sourceAnalysisGateway, libraryDetailsGatewayProvider,
                        libraryMutationGatewayProvider, libraryRootPath, onLibraryQueryChanged,
                        settingsSnapshot, onUpdateSettings,
                        { navigate(AppDestination.Add) }, { playbackAsset = it },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) @Composable internal fun FixedRegionScaffold(title: String, destination: AppDestination, onDestinationSelected: (AppDestination) -> Unit, content: @Composable (PaddingValues) -> Unit) { Scaffold(modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Text(title) }) }, bottomBar = { NavigationBar { AppDestination.entries.forEach { item -> NavigationBarItem(selected = destination == item, onClick = { onDestinationSelected(item) }, icon = { Box(Modifier.sizeIn(minWidth = MidnightTransit.MinimumTouchTarget, minHeight = MidnightTransit.MinimumTouchTarget).semantics { contentDescription = item.accessibilityLabel }, contentAlignment = Alignment.Center) { Text(item.label.take(1)) } }, label = { Text(item.label) }) } } }, content = content) }
@Composable private fun DestinationContent(destination: AppDestination, padding: PaddingValues, initialSharedUrl: String?, libraryState: LibraryScreenState, downloadsState: DownloadsScreenState, downloadControlGateway: AppDownloadControlGateway?, sourceAnalysisGateway: AppSourceAnalysisGateway?, libraryDetailsGatewayProvider: () -> AppLibraryDetailsGateway?, libraryMutationGatewayProvider: () -> AppLibraryMutationGateway?, libraryRootPath: String?, onLibraryQueryChanged: (String) -> Unit, settingsSnapshot: AppSettingsSnapshot, onUpdateSettings: (AppSettingsMutation.() -> Unit) -> Unit, onAdd: () -> Unit, onPlay: (LocalPlaybackAsset) -> Unit) { Box(Modifier.fillMaxSize().padding(padding)) { when (destination) { AppDestination.Library -> LibraryScreen(onAdd, onPlay, libraryState, settingsSnapshot, onUpdateSettings, libraryDetailsGatewayProvider, libraryMutationGatewayProvider, libraryRootPath, onLibraryQueryChanged); AppDestination.Downloads -> DownloadsScreen(downloadsState, downloadControlGateway); AppDestination.Add -> AddScreen(PaddingValues(), initialSharedUrl, sourceAnalysisGateway, downloadControlGateway, settingsSnapshot); AppDestination.Settings -> SettingsScreen(PaddingValues(), settingsSnapshot, onUpdateSettings) } } }
@Composable private fun SettingsScreen(padding: PaddingValues, settings: AppSettingsSnapshot, onUpdateSettings: (AppSettingsMutation.() -> Unit) -> Unit) { var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }; if (section == null) SettingsHub(padding) { section = it } else SettingsPage(padding, section!!, settings, onUpdateSettings) { section = null } }
@Composable private fun SettingsHub(padding: PaddingValues, onOpen: (SettingsSection) -> Unit) { Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Text("Choose a settings category. Primary settings stay on dedicated fixed-layout pages."); SettingsSection.entries.forEach { section -> OutlinedButton(onClick = { onOpen(section) }, modifier = Modifier.fillMaxWidth().weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(section.label) } } } }
@Composable private fun SettingsPage(padding: PaddingValues, section: SettingsSection, settings: AppSettingsSnapshot, onUpdateSettings: (AppSettingsMutation.() -> Unit) -> Unit, onBack: () -> Unit) { Column(Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(section.label); OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Back") } }; when (section) { SettingsSection.Downloads -> { OutlinedButton(onClick = { onUpdateSettings { defaultQuality = if (settings.defaultQuality == "Best compatible") "Audio only" else "Best compatible" } }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Default quality: ${settings.defaultQuality}") }; SettingToggle("Wi-Fi only", settings.wifiOnlyDownloads) { checked -> onUpdateSettings { wifiOnlyDownloads = checked } }; OutlinedButton(onClick = { onUpdateSettings { maxConcurrentDownloads = if (settings.maxConcurrentDownloads >= 4) 1 else settings.maxConcurrentDownloads + 1 } }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Concurrent downloads: ${settings.maxConcurrentDownloads}") }; OutlinedButton(onClick = { onUpdateSettings { subtitleDefault = if (settings.subtitleDefault == "Preferred language") "None" else "Preferred language" } }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Subtitles: ${settings.subtitleDefault}") }; SettingValue("Retry", "Automatic runtime policy") }; SettingsSection.Playback -> { SettingToggle("Remember position", settings.rememberPlaybackPosition) { checked -> onUpdateSettings { rememberPlaybackPosition = checked } }; OutlinedButton(onClick = { onUpdateSettings { playbackSpeed = nextPlaybackSettingSpeed(settings.playbackSpeed) } }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Default speed: ${settings.playbackSpeed}×") } }; SettingsSection.Storage -> StorageSettingsPage(); SettingsSection.Appearance -> { OutlinedButton(onClick = { onUpdateSettings { appearance = nextAppearanceSetting(settings.appearance) } }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Theme: ${settings.appearance.name}") }; OutlinedButton(onClick = { onUpdateSettings { libraryLayout = if (settings.libraryLayout == LibraryLayoutSetting.List) LibraryLayoutSetting.Grid else LibraryLayoutSetting.List } }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Library layout: ${settings.libraryLayout.name}") } }; SettingsSection.About -> AboutSettingsPage() } } }
@Composable private fun StorageSettingsPage() { val context = LocalContext.current; val manager = remember(context) { StorageSettingsManager.open(context) }; var summary by remember { mutableStateOf(manager.summarize()) }; var pendingCleanup by remember { mutableStateOf<ManagedCleanup?>(null) }; fun refresh() { summary = manager.summarize() }; SettingValue("Managed media", formatStorageBytes(summary.mediaBytes)); SettingValue("Database", formatStorageBytes(summary.databaseBytes)); SettingValue("Incomplete", formatStorageBytes(summary.partialBytes)); SettingValue("Cache", formatStorageBytes(summary.cacheBytes)); SettingValue("Free", formatStorageBytes(summary.freeBytes)); val pending = pendingCleanup; if (pending == null) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { OutlinedButton(onClick = { pendingCleanup = ManagedCleanup.Cache }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Clear cache") }; OutlinedButton(onClick = { pendingCleanup = ManagedCleanup.Incomplete }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Clean incomplete") } } } else { Text("Confirm destructive cleanup of " + if (pending == ManagedCleanup.Cache) "cache files?" else "incomplete transfer files?"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { OutlinedButton(onClick = { pendingCleanup = null }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Cancel") }; Button(onClick = { manager.cleanup(pending); pendingCleanup = null; refresh() }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Confirm") } } } }
private fun formatStorageBytes(bytes: Long): String = when { bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)); bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024.0 * 1024.0)); bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024.0); else -> "$bytes B" }
@Composable private fun SettingValue(label: String, value: String) { Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Text(value, textAlign = TextAlign.End) } }
@Composable private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) { Row(Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label); Switch(checked = checked, onCheckedChange = onCheckedChange) } }
private fun nextPlaybackSettingSpeed(current: Float): Float = when { current < 1f -> 1f; current < 1.25f -> 1.25f; current < 1.5f -> 1.5f; current < 2f -> 2f; else -> 0.75f }
private fun nextAppearanceSetting(current: AppearanceSetting): AppearanceSetting = when (current) { AppearanceSetting.System -> AppearanceSetting.Light; AppearanceSetting.Light -> AppearanceSetting.Dark; AppearanceSetting.Dark -> AppearanceSetting.System }

@Composable
private fun AddScreen(
    padding: PaddingValues,
    initialSharedUrl: String?,
    sourceGateway: AppSourceAnalysisGateway?,
    downloadControlGateway: AppDownloadControlGateway?,
    settings: AppSettingsSnapshot,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val sourceAnalysisUseCase = remember(sourceGateway) { sourceGateway?.let(::SourceAnalysisUseCase) }
    var url by rememberSaveable(initialSharedUrl) { mutableStateOf(initialSharedUrl.orEmpty()) }
    var setup by rememberSaveable(initialSharedUrl) { mutableStateOf<DownloadSetupState?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    fun cancelSupersededAnalysis() {
        sourceAnalysisUseCase?.cancelActive()
        analysisJob?.cancel()
        analysisJob = null
        analyzing = false
    }

    DisposableEffect(sourceAnalysisUseCase) {
        onDispose {
            sourceAnalysisUseCase?.cancelActive()
            analysisJob?.cancel()
        }
    }

    if (advanced) {
        val activeSetup = setup
        if (activeSetup != null) {
            AdvancedDownloadOptions(
                padding,
                activeSetup,
                { appliedSetup ->
                    setup = appliedSetup
                    advanced = false
                    status = "Download options applied."
                },
            ) { advanced = false }
            return
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Text("Download a supported video for offline playback.")
        Text("Settings: ${AddWorkflowPolicy.downloadSettingsSummary(settings.wifiOnlyDownloads, settings.maxConcurrentDownloads)}")
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                cancelSupersededAnalysis()
                setup = null
                status = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Video URL") },
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            OutlinedButton(
                onClick = {
                    val pasted = AddWorkflowPolicy.boundedClipboardText(clipboard.getText()?.text)
                    val acceptedText = pasted.acceptedText
                    if (acceptedText == null) {
                        status = pasted.statusMessage
                    } else {
                        cancelSupersededAnalysis()
                        url = acceptedText
                        setup = null
                        status = pasted.statusMessage
                    }
                },
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Paste") }
            Button(
                onClick = {
                    val useCase = sourceAnalysisUseCase
                    if (useCase == null) {
                        status = "Source resolver is unavailable."
                        return@Button
                    }
                    cancelSupersededAnalysis()
                    val started = useCase.begin(url)
                    if (started !is SourceAnalysisState.Loading) {
                        status = sourceAnalysisStatusMessage(started)
                        setup = null
                        return@Button
                    }
                    analyzing = true
                    setup = null
                    status = "Resolving source…"
                    analysisJob = scope.launch {
                        val resolved = withContext(Dispatchers.IO) {
                            useCase.analyzeBlocking(started.ticket)
                        }
                        if (resolved is SourceAnalysisState.Superseded) return@launch
                        analyzing = false
                        analysisJob = null
                        if (resolved !is SourceAnalysisState.Resolved) {
                            status = sourceAnalysisStatusMessage(resolved)
                            return@launch
                        }
                        val analysis = resolved.analysis
                        val qualityLabels = AddWorkflowPolicy.qualityLabels(
                            analysis.qualityOptions.map { it.label },
                            analysis.qualityLabel,
                        )
                        val preferredQualityLabel = AddWorkflowPolicy.preferredQualityLabel(
                            qualityLabels,
                            settings.defaultQuality,
                            analysis.qualityLabel,
                        )
                        val qualityChoiceIdsByLabel =
                            analysis.qualityOptions.associate { it.label to it.choiceId }
                        val qualityEstimatedBytesByLabel =
                            analysis.qualityOptions.associate { it.label to it.estimatedBytes }
                        val qualitySeparateAudioByLabel =
                            analysis.qualityOptions.associate { it.label to it.separateAudio }
                        val subtitleTrackIdsByLabel = analysis.subtitleOptions.associate { option ->
                            sourceSubtitleLabel(
                                option.label,
                                option.language,
                                option.autoGenerated,
                            ) to option.trackId
                        }
                        val audioFormatIdsByLabel =
                            analysis.audioOptions.associate { it.label to it.formatId }
                        val preferredSubtitleTrackId =
                            if (settings.subtitleDefault == "None") null
                            else analysis.subtitleOptions.firstOrNull { !it.autoGenerated }?.trackId
                                ?: analysis.subtitleOptions.firstOrNull()?.trackId
                        setup = DownloadSetupState(
                            sourceUrl = analysis.sourceUrl,
                            title = analysis.title,
                            durationLabel = analysis.durationMs?.let(::formatSetupDuration)
                                ?: "Unknown duration",
                            qualityLabel = preferredQualityLabel,
                            estimatedSizeLabel = qualityEstimatedBytesByLabel[preferredQualityLabel]
                                ?.let(::formatSetupBytes) ?: "Size unavailable",
                            readyForDownload = true,
                            thumbnailUrl = analysis.thumbnailUrl,
                            sourceProvider = analysis.sourceProvider,
                            sourceMediaId = analysis.sourceMediaId,
                            qualityOptions = qualityLabels,
                            subtitleOptions = subtitleTrackIdsByLabel.keys.toList(),
                            audioOptions = audioFormatIdsByLabel.keys.toList(),
                            containerOptions = analysis.containerOptions,
                            qualityChoiceIdsByLabel = qualityChoiceIdsByLabel,
                            qualityEstimatedBytesByLabel = qualityEstimatedBytesByLabel,
                            qualitySeparateAudioByLabel = qualitySeparateAudioByLabel,
                            subtitleTrackIdsByLabel = subtitleTrackIdsByLabel,
                            audioFormatIdsByLabel = audioFormatIdsByLabel,
                            selectedQualityChoiceId = qualityChoiceIdsByLabel[preferredQualityLabel],
                            selectedSubtitleTrackId = preferredSubtitleTrackId,
                            selectedAudioFormatId = null,
                        )
                        status = null
                    }
                },
                enabled = url.isNotBlank() && sourceAnalysisUseCase != null && !analyzing && !scheduling,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text(if (analyzing) "Analyzing…" else "Analyze") }
        }
        Text("Supports recognized YouTube video URLs. Playlists and channel pages are not supported.")
        status?.let { Text(it) }
        setup?.let { setupState ->
            DownloadSetupPreview(setupState, { advanced = true }) {
                val gateway = downloadControlGateway
                if (gateway == null) {
                    status = "Download scheduler is unavailable."
                    return@DownloadSetupPreview
                }
                scheduling = true
                status =
                    "Scheduling ${setupState.qualityLabel} download with ${AddWorkflowPolicy.downloadSettingsSummary(settings.wifiOnlyDownloads, settings.maxConcurrentDownloads)}…"
                val jobId = setupState.sourceUrl
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        gateway.enqueue(
                            jobId,
                            DownloadSelectionOptions(
                                qualityChoiceId = setupState.selectedQualityChoiceId,
                                subtitleTrackId = setupState.selectedSubtitleTrackId,
                                audioFormatId = setupState.selectedAudioFormatId,
                            ),
                        )
                    }
                    scheduling = false
                    result.error?.let {
                        status = it.message
                        return@launch
                    }
                    status = if (result.value == true) {
                        "Download scheduled for ${setupState.qualityLabel} with ${AddWorkflowPolicy.downloadSettingsSummary(settings.wifiOnlyDownloads, settings.maxConcurrentDownloads)}."
                    } else {
                        "Download was not queued."
                    }
                }
            }
        }
    }
}

private fun sourceAnalysisStatusMessage(state: SourceAnalysisState): String = when (state) {
    SourceAnalysisState.Idle -> "Source resolver is idle."
    is SourceAnalysisState.Loading -> "Resolving source…"
    is SourceAnalysisState.Resolved -> ""
    is SourceAnalysisState.Unsupported -> state.message
    is SourceAnalysisState.NetworkFailure -> state.error.message
    is SourceAnalysisState.SourceChanged -> state.error.message
    is SourceAnalysisState.Failed -> state.error.message
    is SourceAnalysisState.Superseded -> "Source analysis was superseded."
}

private fun formatSetupDuration(durationMs: Long): String { val seconds = durationMs / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
private fun formatSetupBytes(bytes: Long): String = if (bytes >= 1024L * 1024L) "%.1f MB".format(bytes.toDouble() / (1024.0 * 1024.0)) else "$bytes bytes"
@Composable
private fun ColumnScope.DownloadSetupPreview(
    setup: DownloadSetupState,
    onOptions: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            Text("Download setup")
            Text(setup.title)
            val sourceIdentity = listOfNotNull(setup.sourceProvider, setup.sourceMediaId)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" / ")
            Text("Source: ${sourceIdentity ?: setup.sourceUrl}")
            Text("Canonical URL: ${setup.sourceUrl}")
            setup.thumbnailUrl?.let { SetupThumbnailPreview(it) }
            Text("Quality options: ${AddWorkflowPolicy.optionSummary(setup.qualityOptions, setup.qualityLabel)}")
            Text("${setup.durationLabel} · ${setup.qualityLabel} · ${setup.estimatedSizeLabel}")
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
        ) {
            OutlinedButton(
                onClick = onOptions,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Options") }
            Button(
                onClick = onDownload,
                enabled = setup.readyForDownload,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Download") }
        }
    }
}
@Composable
private fun AdvancedDownloadOptions(
    padding: PaddingValues,
    setup: DownloadSetupState,
    onApply: (DownloadSetupState) -> Unit,
    onBack: () -> Unit,
) {
    var edited by remember(setup) { mutableStateOf(setup) }
    val requiresSeparateAudio = edited.qualitySeparateAudioByLabel[edited.qualityLabel] == true
    Column(
        Modifier.fillMaxSize().padding(padding).padding(MidnightTransit.ScreenSpacing)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Download options")
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Back") }
        }
        SettingValue("Quality choices", AddWorkflowPolicy.optionSummary(edited.qualityOptions, edited.qualityLabel))
        edited.qualityOptions.forEach { quality ->
            OutlinedButton(
                onClick = {
                    edited = setupWithQuality(edited, quality).copy(
                        selectedAudioFormatId = if (edited.qualitySeparateAudioByLabel[quality] == true) {
                            edited.selectedAudioFormatId
                        } else {
                            null
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Select $quality") }
        }

        SettingValue(
            "Audio choices",
            if (requiresSeparateAudio) {
                AddWorkflowPolicy.optionSummary(edited.audioOptions, "Automatic compatible audio")
            } else {
                "Embedded in selected format"
            },
        )
        if (requiresSeparateAudio && edited.audioOptions.size > 1) {
            OutlinedButton(
                onClick = { edited = edited.copy(selectedAudioFormatId = null) },
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Select automatic audio") }
            edited.audioOptions.forEach { label ->
                OutlinedButton(
                    onClick = {
                        edited = edited.copy(
                            selectedAudioFormatId = edited.audioFormatIdsByLabel[label],
                        )
                    },
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
                ) { Text("Select $label") }
            }
        }

        SettingValue(
            "Subtitle tracks",
            AddWorkflowPolicy.optionSummary(edited.subtitleOptions, "None reported by source"),
        )
        OutlinedButton(
            onClick = { edited = edited.copy(selectedSubtitleTrackId = null) },
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
        ) { Text("Select no subtitles") }
        edited.subtitleOptions.forEach { label ->
            OutlinedButton(
                onClick = {
                    edited = edited.copy(
                        selectedSubtitleTrackId = edited.subtitleTrackIdsByLabel[label],
                    )
                },
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Select $label") }
        }

        SettingValue(
            "Container paths",
            AddWorkflowPolicy.optionSummary(edited.containerOptions, "No compatible container"),
        )
        Text("Container follows the selected source quality; conversion and mux-only paths are not offered.")
        Button(
            onClick = { onApply(edited) },
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
        ) { Text("Apply options") }
    }
}
@Composable
private fun SetupThumbnailPreview(thumbnailUrl: String) {
    val accepted = SetupThumbnailPolicy.acceptedUrl(thumbnailUrl)
    val bitmap by produceState<android.graphics.Bitmap?>(null, accepted) {
        value = if (accepted == null) null else withContext(Dispatchers.IO) {
            loadBoundedSetupThumbnail(accepted)
        }
    }
    Text("Thumbnail preview")
    val loaded = bitmap
    if (loaded == null) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) { Text("Preview unavailable") }
    } else {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Video thumbnail",
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun loadBoundedSetupThumbnail(url: String): android.graphics.Bitmap? = runCatching {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = false
    connection.connectTimeout = SetupThumbnailPolicy.ConnectTimeoutMs
    connection.readTimeout = SetupThumbnailPolicy.ReadTimeoutMs
    connection.requestMethod = "GET"
    try {
        connection.connect()
        if (connection.responseCode !in 200..299) return@runCatching null
        val declared = connection.contentLengthLong
        if (declared > SetupThumbnailPolicy.MaxBytes) return@runCatching null
        val bytes = connection.inputStream.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (output.size() + read > SetupThumbnailPolicy.MaxBytes) return@runCatching null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun sourceSubtitleLabel(label: String?, language: String, autoGenerated: Boolean): String {
    val base = label?.takeIf { it.isNotBlank() } ?: language
    return if (autoGenerated) "$base (auto)" else base
}

private fun setupWithQuality(setup: DownloadSetupState, qualityLabel: String): DownloadSetupState = setup.copy(
    qualityLabel = qualityLabel,
    estimatedSizeLabel = setup.qualityEstimatedBytesByLabel[qualityLabel]?.let(::formatSetupBytes) ?: "Size unavailable",
    selectedQualityChoiceId = setup.qualityChoiceIdsByLabel[qualityLabel],
)
