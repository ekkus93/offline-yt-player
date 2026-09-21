package com.ekkus.offlineytplayer.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsOperationalScreenTest {
    @Test
    fun downloadsScreenExposesEveryRuntimeFilter() {
        assertEquals(
            listOf("All", "Active", "Paused", "Failed", "Completed"),
            DownloadScreenPolicy.Filters,
        )
    }

    @Test
    fun downloadsScreenFiltersAgainstCurrentDurableState() {
        val active = row(DownloadUiState.Active)
        val completed = row(DownloadUiState.Completed)
        assertTrue(DownloadScreenPolicy.matchesFilter(active, "All"))
        assertTrue(DownloadScreenPolicy.matchesFilter(active, "Active"))
        assertFalse(DownloadScreenPolicy.matchesFilter(completed, "Active"))
        assertTrue(DownloadScreenPolicy.matchesFilter(completed, "Completed"))
    }

    @Test
    fun completedDownloadsDoNotExposeIllegalCancelAction() {
        assertEquals(
            listOf(DownloadRowAction.Details),
            DownloadScreenPolicy.legalActions(row(DownloadUiState.Completed)),
        )
        assertEquals(
            listOf(DownloadRowAction.Pause, DownloadRowAction.Cancel),
            DownloadScreenPolicy.legalActions(row(DownloadUiState.Active)),
        )
        assertEquals(
            listOf(DownloadRowAction.Resume, DownloadRowAction.Cancel),
            DownloadScreenPolicy.legalActions(row(DownloadUiState.Paused)),
        )
        assertEquals(
            listOf(DownloadRowAction.Retry, DownloadRowAction.Cancel),
            DownloadScreenPolicy.legalActions(row(DownloadUiState.Failed)),
        )
    }

    @Test
    fun downloadRowsShowProgressStateErrorSpeedAndEtaFields() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt").readText()
        assertTrue(source.contains("DownloadScreenPolicy.detail(row)"))
        assertTrue(source.contains("row.stateLabel"))
        assertTrue(source.contains("row.speedLabel"))
        assertTrue(source.contains("row.etaLabel"))
        assertTrue(source.contains("row.error?.let"))
    }

    private fun row(state: DownloadUiState): DownloadRowModel = DownloadRowModel(
        id = state.name.lowercase(),
        title = state.name,
        state = state,
        percent = 42,
        size = "42 / 100 bytes",
        stateLabel = state.name,
        speedLabel = "Speed unavailable",
        etaLabel = "ETA unavailable",
    )
}
