package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.settings.AppSettingsMutation
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.LibraryLayoutSetting
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real-Compose behavior of the Library/Downloads collection screens. */
@RunWith(AndroidJUnit4::class)
class CollectionScreensInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val rows = listOf(
        LibraryRowModel("item-1", "Alpha clip", "720p · 0:42"),
        LibraryRowModel("item-2", "Beta clip", "1080p · rain"),
    )

    @Test
    fun listLayoutIsRenderedAndToggleEmitsGrid() = assertLayoutToggle(
        current = LibraryLayoutSetting.List,
        expectedLabel = "Grid",
        expectedNext = LibraryLayoutSetting.Grid,
    )

    @Test
    fun gridLayoutIsRenderedAndToggleEmitsList() = assertLayoutToggle(
        current = LibraryLayoutSetting.Grid,
        expectedLabel = "List",
        expectedNext = LibraryLayoutSetting.List,
    )

    private fun assertLayoutToggle(
        current: LibraryLayoutSetting,
        expectedLabel: String,
        expectedNext: LibraryLayoutSetting,
    ) {
        val emitted = mutableListOf<LibraryLayoutSetting>()
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(rows),
                settings = AppSettingsSnapshot(libraryLayout = current),
                onUpdateSettings = { mutation ->
                    emitted += AppSettingsMutation(AppSettingsSnapshot(libraryLayout = current))
                        .apply(mutation).build().libraryLayout
                },
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Filter: All · ${current.name}").assertIsDisplayed()
        compose.onNodeWithText("Alpha clip").assertIsDisplayed()
        compose.onNodeWithText("Beta clip").assertIsDisplayed()
        compose.onNodeWithText(expectedLabel).performClick()
        compose.runOnIdle { assertEquals(listOf(expectedNext), emitted) }
    }

    @Test
    fun librarySearchFiltersRepositoryRowsByTitleAndDetail() {
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(rows),
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        val search = compose.onNode(hasSetTextAction())

        search.performTextInput("alpha")
        compose.onNodeWithText("Alpha clip").assertIsDisplayed()
        compose.onNodeWithText("Beta clip").assertDoesNotExist()

        search.performTextClearance()
        search.performTextInput("RAIN")
        compose.onNodeWithText("Beta clip").assertIsDisplayed()
        compose.onNodeWithText("Alpha clip").assertDoesNotExist()

        search.performTextClearance()
        compose.onNodeWithText("Alpha clip").assertIsDisplayed()
        compose.onNodeWithText("Beta clip").assertIsDisplayed()
    }

    @Test
    fun addScreenSummarizesTheStoredDownloadSettings() {
        compose.setContent {
            OfflineYTPlayerApp(
                settingsSnapshot = AppSettingsSnapshot(wifiOnlyDownloads = false, maxConcurrentDownloads = 3),
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Settings: Any network · 3 concurrent").assertIsDisplayed()
    }

    @Test
    fun downloadRowsRenderStateProgressSpeedEtaAndError() {
        compose.setContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "job-1",
                            title = "Failing download",
                            state = DownloadUiState.Failed,
                            percent = 42,
                            size = "42 / 100 bytes",
                            error = "Network lost",
                            stateLabel = "Failed",
                            speedLabel = "1.5 MB/s",
                            etaLabel = "ETA 0:30",
                        ),
                    ),
                ),
                controlGateway = null,
            )
        }

        compose.onNodeWithText("Failing download").assertIsDisplayed()
        compose.onNodeWithText("Failed · 42% · 42 / 100 bytes · 1.5 MB/s · ETA 0:30").assertIsDisplayed()
        compose.onNodeWithText("Error: Network lost").assertIsDisplayed()
    }
}
