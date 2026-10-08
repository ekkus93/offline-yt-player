package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

/** RMD-604: behavioral Compose navigation and saved-state restoration. */
class LifecycleStateRestorationComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun libraryQueryAndAddDraftSurviveNavigationAndSaveRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            OfflineYTPlayerApp(
                libraryState = LibraryScreenState.Ready(
                    listOf(
                        LibraryRowModel("alpha", "Alpha fixture", "720p"),
                        LibraryRowModel("beta", "Beta fixture", "480p"),
                    ),
                ),
                downloadsState = DownloadsScreenState.Ready(emptyList()),
            )
        }

        compose.onNodeWithText("Search library").performTextInput("Alpha")
        compose.onNodeWithText("Alpha fixture").assertIsDisplayed()
        compose.onNodeWithText("Beta fixture").assertDoesNotExist()

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Video URL").performTextInput("https://youtu.be/dQw4w9WgXcQ")
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Alpha fixture").assertIsDisplayed()
        compose.onNodeWithText("Beta fixture").assertDoesNotExist()

        // This tests Compose saved-state restoration, not Activity process death.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Alpha fixture").assertIsDisplayed()
        compose.onNodeWithText("Beta fixture").assertDoesNotExist()

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("https://youtu.be/dQw4w9WgXcQ").assertIsDisplayed()
    }
}
