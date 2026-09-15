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
internal fun LibraryScreen(onAdd: () -> Unit, onPlay: (LocalPlaybackAsset) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var layout by rememberSaveable { mutableStateOf(LibraryLayout.List) }
    val allItems = emptyList<LibraryRowModel>()
    val visibleItems = allItems.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
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
            )
            OutlinedButton(
                onClick = { layout = if (layout == LibraryLayout.List) LibraryLayout.Grid else LibraryLayout.List },
                modifier = Modifier.sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
            ) { Text(if (layout == LibraryLayout.List) "Grid" else "List") }
        }
        Text("Filter: All · ${layout.name}")
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
                items(visibleItems, key = { it.id }) { item -> LibraryItemRow(item, onPlay) }
            }
        }
    }
}

@Composable
private fun LibraryItemRow(item: LibraryRowModel, onPlay: (LocalPlaybackAsset) -> Unit) {
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
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Details") }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Remove") }
        }
    }
}

@Composable
internal fun DownloadsScreen() {
    var filter by rememberSaveable { mutableStateOf("All") }
    val rows = emptyList<DownloadRowModel>()
    Column(
        Modifier.fillMaxSize().padding(MidnightTransit.ScreenSpacing),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            listOf("All", "Active", "Failed").forEach { value ->
                OutlinedButton(
                    onClick = { filter = value },
                    modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget),
                ) { Text(if (filter == value) "[$value]" else value) }
            }
        }
        if (rows.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No downloads yet")
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
                items(rows, key = { it.id }) { row -> DownloadRow(row) }
            }
        }
    }
}

@Composable
private fun DownloadRow(row: DownloadRowModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
        Text(row.title)
        Text("${row.state.name} · ${row.percent.coerceIn(0, 100)}% · ${row.size}")
        row.error?.let { Text("Error: $it") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing)) {
            when (row.state) {
                DownloadUiState.Active -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Pause") }
                DownloadUiState.Paused -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Resume") }
                DownloadUiState.Failed -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Retry") }
                DownloadUiState.Completed -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Details") }
            }
            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).sizeIn(minHeight = MidnightTransit.MinimumTouchTarget)) { Text("Cancel") }
        }
    }
}
