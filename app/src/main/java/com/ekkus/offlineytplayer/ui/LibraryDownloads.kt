package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalPlaybackPolicy

internal enum class LibraryLayout { List, Grid }
internal enum class DownloadUiState { Active, Paused, Failed, Completed }

internal data class LibraryRowModel(
    val id: String,
    val title: String,
    val detail: String,
    val completed: Boolean = true,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val resumePositionMs: Long = 0,
)

internal data class DownloadRowModel(
    val id: String,
    val title: String,
    val state: DownloadUiState,
    val percent: Int,
    val size: String,
    val error: String? = null,
)

internal sealed class LibraryScreenState {
    object Loading : LibraryScreenState()
    data class Ready(val rows: List<LibraryRowModel>) : LibraryScreenState()
    data class Unavailable(val reason: String) : LibraryScreenState()
    data class Failed(val message: String) : LibraryScreenState()
}

internal sealed class DownloadsScreenState {
    object Loading : DownloadsScreenState()
    data class Ready(val rows: List<DownloadRowModel>) : DownloadsScreenState()
    data class Unavailable(val reason: String) : DownloadsScreenState()
    data class Failed(val message: String) : DownloadsScreenState()
}

internal object CollectionLayoutPolicy {
    const val LibraryFixedControlRows = 2
    const val DownloadFixedControlRows = 1
    const val MinimumActionHeightDp = 48
    const val HasHorizontalControlScrolling = false
}

internal object LibraryPlaybackRoute {
    fun assetFor(row: LibraryRowModel): LocalPlaybackAsset? {
        if (!row.completed) return null
        val videoPath = row.videoPath?.takeIf { it.isNotBlank() } ?: return null
        return LocalPlaybackPolicy.validate(
            LocalPlaybackAsset(
                videoPath = videoPath,
                audioPath = row.audioPath,
                title = row.title,
                startPositionMs = row.resumePositionMs,
            ),
        )
    }
}

@Composable
internal fun LibraryScreen(
    onAdd: () -> Unit,
    onPlay: (LocalPlaybackAsset) -> Unit,
    state: LibraryScreenState = LibraryScreenState.Unavailable(
        "Library repository is not connected yet; no empty-library claim is being made.",
    ),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var layout by rememberSaveable { mutableStateOf(LibraryLayout.List) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val readyRows = (state as? LibraryScreenState.Ready)?.rows.orEmpty()
    val visibleItems = readyRows.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    Column(
        Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Search library") },
                singleLine = true,
                enabled = state is LibraryScreenState.Ready,
            )
            OutlinedButton(
                onClick = { layout = if (layout == LibraryLayout.List) LibraryLayout.Grid else LibraryLayout.List },
                modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
                enabled = state is LibraryScreenState.Ready,
            ) { Text(if (layout == LibraryLayout.List) "Grid" else "List") }
        }
        Text("Filter: All · ${layout.name}")
        message?.let { Text(it) }
        when (state) {
            LibraryScreenState.Loading -> RepositoryStatus("Loading library repository state…")
            is LibraryScreenState.Unavailable -> RepositoryStatus(state.reason)
            is LibraryScreenState.Failed -> RepositoryStatus("Library repository failed: ${state.message}")
            is LibraryScreenState.Ready -> {
                if (visibleItems.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (query.isBlank()) "No offline videos yet" else "No matching videos")
                    }
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
                    ) { Text("Add video") }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
                        items(visibleItems, key = { it.id }) { item ->
                            LibraryItemRow(
                                item = item,
                                onPlay = onPlay,
                                onDetails = { selected -> message = "${selected.title}: ${selected.detail}" },
                                onRemove = { selected -> message = "Remove requires repository-backed deletion for ${selected.title}." },
                            )
                        }
                    }
                }
            }
        }
        if (state !is LibraryScreenState.Ready) {
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Add video") }
        }
    }
}

@Composable
private fun RepositoryStatus(text: String) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text)
    }
}

@Composable
private fun LibraryItemRow(
    item: LibraryRowModel,
    onPlay: (LocalPlaybackAsset) -> Unit,
    onDetails: (LibraryRowModel) -> Unit,
    onRemove: (LibraryRowModel) -> Unit,
) {
    val playbackAsset = LibraryPlaybackRoute.assetFor(item)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text(item.title)
        Text(item.detail)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            Button(
                onClick = { playbackAsset?.let(onPlay) },
                enabled = playbackAsset != null,
                modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text("Play") }
            OutlinedButton(onClick = { onDetails(item) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Details") }
            OutlinedButton(onClick = { onRemove(item) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Remove") }
        }
    }
}

@Composable
internal fun DownloadsScreen(
    state: DownloadsScreenState = DownloadsScreenState.Unavailable(
        "Downloads repository is not connected yet; no empty-queue claim is being made.",
    ),
    controlGateway: AppDownloadControlGateway? = null,
) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = (state as? DownloadsScreenState.Ready)?.rows.orEmpty()
    val visibleRows = rows.filter { row ->
        filter == "All" || row.state.name == filter
    }
    Column(
        Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            listOf("All", "Active", "Failed").forEach { value ->
                OutlinedButton(
                    onClick = { filter = value },
                    enabled = state is DownloadsScreenState.Ready,
                    modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
                ) { Text(if (filter == value) "[$value]" else value) }
            }
        }
        message?.let { Text(it) }
        when (state) {
            DownloadsScreenState.Loading -> RepositoryStatus("Loading download queue state…")
            is DownloadsScreenState.Unavailable -> RepositoryStatus(state.reason)
            is DownloadsScreenState.Failed -> RepositoryStatus("Downloads repository failed: ${state.message}")
            is DownloadsScreenState.Ready -> {
                if (visibleRows.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (rows.isEmpty()) "No downloads yet" else "No downloads match the $filter filter")
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
                        items(visibleRows, key = { it.id }) { row ->
                            DownloadRow(
                                row = row,
                                onAction = { action ->
                                    val gateway = controlGateway
                                    message = if (gateway == null) {
                                        "Download control gateway is not connected for ${row.title}."
                                    } else if (DownloadRowControlBinding.invoke(action, row.id, gateway)) {
                                        "${action.name} requested for ${row.title}."
                                    } else {
                                        "${action.name} failed for ${row.title}."
                                    }
                                },
                                onDetails = { selected -> message = "${selected.title}: ${selected.state.name} · ${selected.percent.coerceIn(0, 100)}% · ${selected.size}" },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    row: DownloadRowModel,
    onAction: (DownloadRowAction) -> Unit,
    onDetails: (DownloadRowModel) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text(row.title)
        Text("${row.state.name} · ${row.percent.coerceIn(0, 100)}% · ${row.size}")
        row.error?.let { Text("Error: $it") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            when (row.state) {
                DownloadUiState.Active -> OutlinedButton(onClick = { onAction(DownloadRowAction.Pause) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Pause") }
                DownloadUiState.Paused -> OutlinedButton(onClick = { onAction(DownloadRowAction.Resume) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Resume") }
                DownloadUiState.Failed -> OutlinedButton(onClick = { onAction(DownloadRowAction.Retry) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Retry") }
                DownloadUiState.Completed -> OutlinedButton(onClick = { onDetails(row) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Details") }
            }
            OutlinedButton(onClick = { onAction(DownloadRowAction.Cancel) }, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Cancel") }
        }
    }
}
