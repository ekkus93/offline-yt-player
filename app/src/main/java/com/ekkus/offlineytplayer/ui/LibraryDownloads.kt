package com.ekkus.offlineytplayer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.resilience.CorruptionRecoveryPolicy
import com.ekkus.offlineytplayer.resilience.RecoveryUiAction
import com.ekkus.offlineytplayer.resilience.RecoveryUiState
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy
import com.ekkus.offlineytplayer.settings.AppSettingsMutation
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.LibraryLayoutSetting

internal enum class LibraryLayout { List, Grid }
internal enum class DownloadUiState { Active, Paused, Failed, Completed }
internal data class LibraryRowModel(val id: String, val title: String, val detail: String, val completed: Boolean = true, val videoPath: String? = null, val audioPath: String? = null, val resumePositionMs: Long = 0)
internal data class DownloadRowModel(val id: String, val title: String, val state: DownloadUiState, val percent: Int, val size: String, val error: String? = null, val stateLabel: String = state.name, val speedLabel: String = "Speed unavailable", val etaLabel: String = "ETA unavailable")
internal sealed class LibraryScreenState { object Loading : LibraryScreenState(); data class Ready(val rows: List<LibraryRowModel>) : LibraryScreenState(); data class Unavailable(val reason: String) : LibraryScreenState(); data class Failed(val message: String) : LibraryScreenState() }
internal sealed class DownloadsScreenState { object Loading : DownloadsScreenState(); data class Ready(val rows: List<DownloadRowModel>) : DownloadsScreenState(); data class Unavailable(val reason: String) : DownloadsScreenState(); data class Failed(val message: String) : DownloadsScreenState() }
internal object CollectionLayoutPolicy { const val LibraryFixedControlRows = 2; const val DownloadFixedControlRows = 1; const val MinimumActionHeightDp = 48; const val HasHorizontalControlScrolling = false }
internal object LibraryPlaybackRoute { fun assetFor(row: LibraryRowModel): LocalPlaybackAsset? { if (!row.completed) return null; val videoPath = row.videoPath?.takeIf { it.isNotBlank() } ?: return null; return LocalPlaybackPolicy.validate(LocalPlaybackAsset(videoPath = videoPath, audioPath = row.audioPath, title = row.title, startPositionMs = row.resumePositionMs, itemId = row.id)) } }
internal object DownloadScreenPolicy { val Filters = listOf("All", "Active", "Paused", "Failed", "Completed"); fun matchesFilter(row: DownloadRowModel, filter: String) = filter == "All" || row.state.name == filter; fun legalActions(row: DownloadRowModel) = when (row.state) { DownloadUiState.Active -> listOf(DownloadRowAction.Pause, DownloadRowAction.Cancel); DownloadUiState.Paused -> listOf(DownloadRowAction.Resume, DownloadRowAction.Cancel); DownloadUiState.Failed -> listOf(DownloadRowAction.Retry, DownloadRowAction.Cancel); DownloadUiState.Completed -> emptyList() }; fun detail(row: DownloadRowModel) = listOf(row.stateLabel, "${row.percent.coerceIn(0, 100)}%", row.size, row.speedLabel, row.etaLabel).joinToString(" · ") }

@Composable internal fun LibraryScreen(onAdd: () -> Unit, onPlay: (LocalPlaybackAsset) -> Unit, state: LibraryScreenState = LibraryScreenState.Unavailable("Library repository is not connected yet; no empty-library claim is being made."), settings: AppSettingsSnapshot = AppSettingsSnapshot(), onUpdateSettings: (AppSettingsMutation.() -> Unit) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }; val layout = if (settings.libraryLayout == LibraryLayoutSetting.Grid) LibraryLayout.Grid else LibraryLayout.List; var message by rememberSaveable { mutableStateOf<String?>(null) }; val readyRows = (state as? LibraryScreenState.Ready)?.rows.orEmpty(); val visibleItems = readyRows.filter { it.matchesLibraryQuery(query) }
    Column(Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), label = { Text("Search library") }, singleLine = true, enabled = state is LibraryScreenState.Ready); OutlinedButton(onClick = { onUpdateSettings { libraryLayout = if (settings.libraryLayout == LibraryLayoutSetting.List) LibraryLayoutSetting.Grid else LibraryLayoutSetting.List } }, modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget), enabled = state is LibraryScreenState.Ready) { Text(if (layout == LibraryLayout.List) "Grid" else "List") } }; Text("Filter: All · ${layout.name}"); message?.let { Text(it) }; when (state) { LibraryScreenState.Loading -> RepositoryStatus("Loading library repository state…"); is LibraryScreenState.Unavailable -> RepositoryStatus(state.reason); is LibraryScreenState.Failed -> { val recovery = CorruptionRecoveryPolicy.uiState("startup", state.message); if (recovery == null) RepositoryStatus("Library repository failed: ${state.message}") else RecoveryStatus(recovery, state.message) }; is LibraryScreenState.Ready -> { if (visibleItems.isEmpty()) { Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(if (query.isBlank()) "No offline videos yet" else "No matching videos") }; Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Add video") } } else LibraryItems(layout, visibleItems, onPlay, { message = "${it.title}: ${it.detail}" }, { message = "Remove requires repository-backed deletion for ${it.title}." }) } }; if (state !is LibraryScreenState.Ready) Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Add video") } }
}
@Composable private fun LibraryItems(layout: LibraryLayout, rows: List<LibraryRowModel>, onPlay: (LocalPlaybackAsset) -> Unit, onDetails: (LibraryRowModel) -> Unit, onRemove: (LibraryRowModel) -> Unit) { when (layout) { LibraryLayout.List -> LazyColumn(Modifier.weightedCollectionRegion(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { items(rows, key = { it.id }) { LibraryItemRow(it, onPlay, onDetails, onRemove) } }; LibraryLayout.Grid -> LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weightedCollectionRegion(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { gridItems(rows, key = { it.id }) { LibraryItemRow(it, onPlay, onDetails, onRemove) } } } }
private fun Modifier.weightedCollectionRegion() = fillMaxWidth()
private fun LibraryRowModel.matchesLibraryQuery(query: String): Boolean { val normalized = query.trim(); return normalized.isBlank() || title.contains(normalized, ignoreCase = true) || detail.contains(normalized, ignoreCase = true) }
@Composable private fun ColumnScope.RepositoryStatus(text: String) { Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(text) } }
@Composable private fun ColumnScope.RecoveryStatus(state: RecoveryUiState, diagnostic: String) {
    val context = LocalContext.current
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Text(state.title)
        Text(state.message)
        if (confirmReset) {
            Text("Reset removes this app's local library database and recovery metadata. Downloaded media files are left in place. This cannot be undone.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
                OutlinedButton(onClick = { confirmReset = false }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Cancel") }
                Button(onClick = { resetLocalDatabase(context.filesDir); (context as? Activity)?.recreate() }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Confirm reset") }
            }
        } else {
            state.actions.forEach { action ->
                OutlinedButton(onClick = {
                    when (action) {
                        RecoveryUiAction.RetryStartup -> (context as? Activity)?.recreate()
                        RecoveryUiAction.ExportDiagnostics -> context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Offline YT Player recovery diagnostics"); putExtra(Intent.EXTRA_TEXT, diagnostic.take(2048)) }, "Export diagnostics"))
                        RecoveryUiAction.OpenSupport -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ekkus93/offline-yt-player/issues")))
                        RecoveryUiAction.ResetLocalData -> confirmReset = true
                        RecoveryUiAction.UpgradeApplication -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ekkus93/offline-yt-player/releases")))
                    }
                }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(action.label()) }
            }
        }
    }
}
private fun RecoveryUiAction.label(): String = when (this) { RecoveryUiAction.RetryStartup -> "Retry recovery"; RecoveryUiAction.ExportDiagnostics -> "Export diagnostics"; RecoveryUiAction.OpenSupport -> "Support"; RecoveryUiAction.ResetLocalData -> "Reset local data"; RecoveryUiAction.UpgradeApplication -> "Upgrade app" }
private fun resetLocalDatabase(filesDir: java.io.File) { listOf("offline-yt-player.sqlite3", "offline-yt-player.sqlite3-wal", "offline-yt-player.sqlite3-shm").forEach { java.io.File(filesDir, it).delete() } }
@Composable private fun LibraryItemRow(item: LibraryRowModel, onPlay: (LocalPlaybackAsset) -> Unit, onDetails: (LibraryRowModel) -> Unit, onRemove: (LibraryRowModel) -> Unit) { val asset = LibraryPlaybackRoute.assetFor(item); Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Text(item.title); Text(item.detail); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Button(onClick = { asset?.let(onPlay) }, enabled = asset != null, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Play") }; OutlinedButton(onClick = { onDetails(item) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Details") }; OutlinedButton(onClick = { onRemove(item) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Remove") } } } }
@Composable internal fun DownloadsScreen(state: DownloadsScreenState = DownloadsScreenState.Unavailable("Downloads repository is not connected yet; no empty-queue claim is being made."), controlGateway: AppDownloadControlGateway? = null) { var filter by rememberSaveable { mutableStateOf("All") }; var message by rememberSaveable { mutableStateOf<String?>(null) }; val rows = (state as? DownloadsScreenState.Ready)?.rows.orEmpty(); val visibleRows = rows.filter { DownloadScreenPolicy.matchesFilter(it, filter) }; Column(Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { DownloadScreenPolicy.Filters.forEach { value -> OutlinedButton(onClick = { filter = value }, enabled = state is DownloadsScreenState.Ready, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(if (filter == value) "[$value]" else value) } } }; message?.let { Text(it) }; when (state) { DownloadsScreenState.Loading -> RepositoryStatus("Loading download queue state…"); is DownloadsScreenState.Unavailable -> RepositoryStatus(state.reason); is DownloadsScreenState.Failed -> RepositoryStatus("Downloads repository failed: ${state.message}"); is DownloadsScreenState.Ready -> { if (visibleRows.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(if (rows.isEmpty()) "No downloads yet" else "No downloads match the $filter filter") } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { items(visibleRows, key = { it.id }) { row -> DownloadRow(row, { action -> val gateway = controlGateway; message = if (gateway == null) "Download control gateway is not connected for ${row.title}." else if (DownloadRowControlBinding.invoke(action, row.id, gateway)) "${action.name} requested for ${row.title}." else "${action.name} failed for ${row.title}." }, { selected -> message = "${selected.title}: ${DownloadScreenPolicy.detail(selected)}" }) } } } } } }
@Composable private fun DownloadRow(row: DownloadRowModel, onAction: (DownloadRowAction) -> Unit, onDetails: (DownloadRowModel) -> Unit) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { Text(row.title); Text(DownloadScreenPolicy.detail(row)); row.error?.let { Text("Error: $it") }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) { DownloadScreenPolicy.legalActions(row).forEach { action -> OutlinedButton(onClick = { onAction(action) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text(action.name) } }; OutlinedButton(onClick = { onDetails(row) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Details") } } } }
