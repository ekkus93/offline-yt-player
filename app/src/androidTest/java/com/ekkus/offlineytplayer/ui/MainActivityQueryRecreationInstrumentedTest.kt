package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RMD-604: Activity recreation (not merely Compose's synthetic saved-state restore)
 * must preserve the visible Library query while MainActivity rebinds the repository.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityQueryRecreationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun realActivityRecreationRestoresLibrarySearchDraft() {
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithText("Search library").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Search library").performTextInput("Alpha")
        compose.onNodeWithText("Alpha").assertExists()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {
            runCatching { compose.onNodeWithText("Alpha").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Alpha").assertExists()
    }
}
