package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceAudioChoice
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.CoreSourceSubtitleChoice
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicateProviderOptionsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun duplicateAudioAndSubtitleLabelsEnqueueSelectedOriginalIds() {
        // Raw duplicate labels must be disambiguated by the actual Add screen.
        val audio = listOf(
            CoreSourceAudioChoice("audio-a", "128 kbps", "m4a", 128_000),
            CoreSourceAudioChoice("audio-b", "128 kbps", "m4a", 160_000),
            CoreSourceAudioChoice("audio-c", "128 kbps (2)", "webm", 96_000),
        )
        val subtitles = listOf(
            CoreSourceSubtitleChoice("sub-a", "en", "English", "vtt", false),
            CoreSourceSubtitleChoice("sub-b", "en", "English", "vtt", false),
            CoreSourceSubtitleChoice("sub-c", "en", "English (2)", "vtt", false),
        )
        val analysis = CoreSourceAnalysis(
            sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
            title = "Duplicate provider choices",
            durationMs = 42_000L,
            thumbnailUrl = null,
            qualityLabel = "720p",
            estimatedBytes = 1024L,
            qualityOptions = listOf(
                CoreSourceQualityChoice("720p", 1024L, "video-720", separateAudio = true),
            ),
            subtitleOptions = subtitles,
            audioOptions = audio,
            containerOptions = listOf("mp4"),
        )
        val source = object : AppSourceAnalysisGateway {
            override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> =
                CoreGatewayResult(analysis, null)
            override fun close() = Unit
        }
        val controls = FakeDownloadControlGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = analysis.sourceUrl,
                sourceAnalysisGateway = source,
                downloadControlGateway = controls,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText("Duplicate provider choices").assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Select 128 kbps (3)").performScrollTo().performClick()
        compose.onNodeWithText("Select English (3)").performScrollTo().performClick()
        compose.onNodeWithText("Apply options").performScrollTo().performClick()
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(10_000) { controls.enqueuedSelections.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(
                DownloadSelectionOptions("video-720", "sub-b", "audio-b"),
                controls.enqueuedSelections.single(),
            )
        }
    }
    @Test
    fun duplicateQualityLabelsEnqueueTheSelectedOriginalChoiceId() {
        val analysis = CoreSourceAnalysis(
            sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
            title = "Duplicate quality choices",
            durationMs = 42_000L,
            thumbnailUrl = null,
            qualityLabel = "720p",
            estimatedBytes = 1024L,
            qualityOptions = listOf(
                CoreSourceQualityChoice("720p", 1024L, "video-a"),
                CoreSourceQualityChoice("720p", 2048L, "video-b"),
                CoreSourceQualityChoice("720p (2)", 4096L, "video-c"),
            ),
            containerOptions = listOf("mp4"),
        )
        val source = object : AppSourceAnalysisGateway {
            override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> =
                CoreGatewayResult(analysis, null)
            override fun close() = Unit
        }
        val controls = FakeDownloadControlGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = analysis.sourceUrl,
                sourceAnalysisGateway = source,
                downloadControlGateway = controls,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText("Duplicate quality choices").assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Select 720p (3)").performScrollTo().performClick()
        compose.onNodeWithText("Apply options").performScrollTo().performClick()
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(10_000) { controls.enqueuedSelections.isNotEmpty() }
        compose.runOnIdle {
            assertEquals("video-b", controls.enqueuedSelections.single().qualityChoiceId)
        }
    }

}
