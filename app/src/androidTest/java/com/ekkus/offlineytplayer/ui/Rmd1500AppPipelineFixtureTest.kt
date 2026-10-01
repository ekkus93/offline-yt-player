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
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Rmd1500AppPipelineFixtureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sharedFixtureUrlEntersAppAnalyzeDownloadSetupAndSchedulingPipeline() {
        val sharedUrl = "https" + "://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val sourceGateway = RecordingSourceAnalysisGateway(
            CoreSourceAnalysis(
                sourceUrl = sharedUrl,
                title = "RMD-1500 app pipeline fixture",
                durationMs = 42_000,
                thumbnailUrl = null,
                qualityLabel = "720p",
                estimatedBytes = 37,
                qualityOptions = listOf(
                    CoreSourceQualityChoice(label = "720p", estimatedBytes = 37, choiceId = "fixture-720p"),
                    CoreSourceQualityChoice(label = "Audio only", estimatedBytes = 17, choiceId = "fixture-audio"),
                ),
            ),
        )
        val downloadControl = FakeDownloadControlGateway()

        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                libraryState = LibraryScreenState.Ready(emptyList()),
                downloadsState = DownloadsScreenState.Ready(emptyList()),
                downloadControlGateway = downloadControl,
                sourceAnalysisGateway = sourceGateway,
                settingsSnapshot = AppSettingsSnapshot(),
                onUpdateSettings = {},
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            sourceGateway.analyzedUrls.isNotEmpty()
        }

        assertEquals(listOf(sharedUrl), sourceGateway.analyzedUrls)
        compose.onNodeWithText("RMD-1500 app pipeline fixture", substring = true).assertIsDisplayed()
        compose.onNodeWithText("720p", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { downloadControl.enqueuedJobIds.isNotEmpty() }

        compose.runOnIdle {
            assertEquals(listOf(sharedUrl), downloadControl.enqueuedJobIds)
            assertEquals(listOf("fixture-720p"), downloadControl.enqueuedChoiceIds)
            assertEquals("fixture-720p", downloadControl.enqueuedSelections.single().qualityChoiceId)
        }
    }
}

private class RecordingSourceAnalysisGateway(
    private val analysis: CoreSourceAnalysis,
) : AppSourceAnalysisGateway {
    val analyzedUrls = mutableListOf<String>()

    override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
        analyzedUrls += sourceUrl
        return CoreGatewayResult(value = analysis.copy(sourceUrl = sourceUrl), error = null)
    }

    override fun close() = Unit
}
