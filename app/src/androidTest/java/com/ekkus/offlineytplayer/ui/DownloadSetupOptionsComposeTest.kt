package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class DownloadSetupOptionsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addScreenRendersSetupEntrypointForDownloadOptions() {
        compose.setContent {
            OfflineYTPlayerApp(initialSharedUrl = "fixture input")
        }

        compose.onNodeWithText("Download a supported video for offline playback.").assertIsDisplayed()
        compose.onNodeWithText("Analyze").assertIsDisplayed()
        compose.onNodeWithText("Options").assertDoesNotExist()
    }
}
