package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Reproduces the reported Samsung Add-screen state on a GitHub Actions emulator. */
class AddResolverAvailabilityInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun validYouTubeUrlCanShowUnavailableResolverError() {
        val diagnostic = "YouTube resolver failed to start (UnsatisfiedLinkError). Analyze is unavailable in this build."
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/AACwyjlk8ko",
                sourceGatewayStartupError = diagnostic,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").assertIsEnabled().performClick()
        compose.onNodeWithText(diagnostic).assertIsDisplayed()
    }

    @Test
    fun userUrlParametersAreCanonicalizedBeforeAnalysis() {
        val source = FakeSourceAnalysisGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/AACwyjlk8ko?is=8sN66gt7NLpTjRGc",
                sourceAnalysisGateway = source,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { source.analyzedUrls.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("https://www.youtube.com/watch?v=AACwyjlk8ko"), source.analyzedUrls)
        }
    }
}
