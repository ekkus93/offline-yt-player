package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadSetupOptionsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun advancedOptionsPersistSelectedQualityBeforeDownload() {
        val sourceGateway = FakeSourceGateway()
        val downloadGateway = FakeDownloadControlGateway()

        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "fixture://input",
                sourceAnalysisGateway = sourceGateway,
                downloadControlGateway = downloadGateway,
            )
        }

        compose.onNodeWithText("Analyze").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Quality options: 720p · Audio only").assertIsDisplayed()

        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Select Audio only").performClick()
        compose.onNodeWithText("Download options applied.").assertIsDisplayed()
        compose.onNodeWithText("4:02 · Audio only · 48.0 MB").assertIsDisplayed()

        compose.onNodeWithText("Download").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Download scheduled for Audio only with Any network · 2 concurrent.").assertIsDisplayed()
        assertEquals(listOf(FakeSourceGateway.CanonicalUrl), downloadGateway.enqueuedJobIds)
    }

    private class FakeSourceGateway : AppSourceAnalysisGateway {
        override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> = CoreGatewayResult(
            value = CoreSourceAnalysis(
                sourceUrl = CanonicalUrl,
                title = "Fixture options video",
                durationMs = 242_000,
                thumbnailUrl = "fixture://thumbnail",
                qualityLabel = "720p",
                estimatedBytes = 50_331_648,
                qualityOptions = listOf(
                    CoreSourceQualityChoice("720p", 50_331_648),
                    CoreSourceQualityChoice("Audio only", 4_194_304),
                ),
            ),
            error = null,
        )

        override fun close() = Unit

        companion object {
            const val CanonicalUrl: String = "fixture://canonical-video"
        }
    }
}
