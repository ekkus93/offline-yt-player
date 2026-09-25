package com.ekkus.offlineytplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProductionComposeBehaviorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun library_empty_and_populated_states_are_behavioral() {
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(emptyList()),
            )
        }
        compose.onNodeWithText("No offline videos yet").assertIsDisplayed()
        compose.onNodeWithText("Add video").assertIsDisplayed()

        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
            )
        }
        compose.onNodeWithText("Fixture video").assertIsDisplayed()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Fixture video: 720p · 0:42").assertIsDisplayed()
    }

    @Test
    fun downloads_actions_invoke_the_real_control_boundary() {
        val controls = FakeDownloadControlGateway()
        compose.setContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "job-1",
                            title = "Fixture download",
                            state = DownloadUiState.Active,
                            percent = 25,
                            size = "25 / 100 bytes",
                        ),
                    ),
                ),
                controlGateway = controls,
            )
        }

        compose.onNodeWithText("Fixture download").assertIsDisplayed()
        compose.onNodeWithText("Pause").performClick()
        compose.runOnIdle { assertEquals(listOf("job-1"), controls.pausedJobIds) }
        compose.onNodeWithText("Pause requested for Fixture download.").assertIsDisplayed()
    }

    @Test
    fun shared_input_opens_add_flow_and_back_stack_remains_navigable() {
        compose.setContent {
            OfflineYTPlayerApp(initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ")
        }

        compose.onNodeWithText("Video URL").assertIsDisplayed()
        compose.onNodeWithText("https://youtu.be/dQw4w9WgXcQ").assertIsDisplayed()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Library repository is not connected yet; no empty-library claim is being made.").assertIsDisplayed()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Video URL").assertIsDisplayed()
    }

    @Test
    fun paste_button_reads_clipboard_into_add_input() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("video-url", "https://youtu.be/dQw4w9WgXcQ"))

        compose.setContent { OfflineYTPlayerApp() }

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Paste").performClick()

        compose.onNodeWithText("https://youtu.be/dQw4w9WgXcQ").assertIsDisplayed()
        compose.onNodeWithText("Pasted clipboard text. Choose Analyze to validate it.").assertIsDisplayed()
    }

    @Test
    fun add_analyze_setup_and_download_use_gateway_boundaries() {
        val source = FakeSourceAnalysisGateway()
        val controls = FakeDownloadControlGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = source,
                downloadControlGateway = controls,
            )
        }

        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("Fixture source").assertIsDisplayed()
            }.isSuccess
        }

        compose.runOnIdle {
            assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ"), source.analyzedUrls)
        }
        compose.onNodeWithText("Download setup").assertIsDisplayed()
        compose.onNodeWithText("Fixture source").assertIsDisplayed()
        compose.onNodeWithText("0:42 · 720p · 12.0 MB").assertIsDisplayed()
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Quality choices").assertIsDisplayed()
        compose.onNodeWithText("720p · Audio only").assertIsDisplayed()
        compose.onNodeWithText("Subtitle tracks").assertIsDisplayed()
        compose.onNodeWithText("Preferred language").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { controls.enqueuedJobIds.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ"), controls.enqueuedJobIds)
        }
    }

    @Test
    fun settings_hub_navigation_exposes_operational_pages() {
        compose.setContent { OfflineYTPlayerApp() }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("Default quality: Best compatible").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Playback").performClick()
        compose.onNodeWithText("Remember position").assertIsDisplayed()
    }
}

private class FakeSourceAnalysisGateway(
    private val analysis: CoreSourceAnalysis = CoreSourceAnalysis(
        sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
        title = "Fixture source",
        durationMs = 42_000,
        thumbnailUrl = "https://example.test/thumb.jpg",
        qualityLabel = "720p",
        estimatedBytes = 12L * 1024L * 1024L,
        qualityOptions = listOf(
            CoreSourceQualityChoice("720p", 12L * 1024L * 1024L),
            CoreSourceQualityChoice("Audio only", 2L * 1024L * 1024L),
        ),
    ),
) : AppSourceAnalysisGateway {
    val analyzedUrls = mutableListOf<String>()

    override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
        analyzedUrls += sourceUrl
        return CoreGatewayResult(value = analysis, error = null)
    }

    override fun close() = Unit
}
