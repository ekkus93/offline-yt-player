package com.ekkus.offlineytplayer.ui

import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceAudioChoice
import com.ekkus.offlineytplayer.coregateway.CoreSourceSubtitleChoice
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Test

class ProductionDownloadSetupBehaviorTest {
    @get:Rule
    val compose = createComposeRule()


    @Test
    fun add_analyze_setup_and_download_use_gateway_boundaries() {
        val source = FakeSourceAnalysisGateway()
        val controls = FakeDownloadControlGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = source,
                downloadControlGateway = controls,
            
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
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
        compose.onNodeWithText("English").assertIsDisplayed()
        compose.onNodeWithText("Select English").performClick()
        compose.onNodeWithText("Select Audio only").performClick()
        compose.onNodeWithText("Apply options").performClick()
        compose.onNodeWithText("0:42 · Audio only · 2.0 MB").assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { controls.enqueuedJobIds.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("https://youtu.be/dQw4w9WgXcQ"), controls.enqueuedJobIds)
            assertEquals(listOf("audio-only"), controls.enqueuedChoiceIds)
            assertEquals(listOf("subtitle-en"), controls.enqueuedSelections.map { it.subtitleTrackId })
        }
    }

    @Test
    fun downloadSetupDoesNotRenderSignedResolvedSourceUrlsOrIdentityTokens() {
        val marker = "RMD1802_SETUP_PRIVATE_MARKER"
        val source = FakeSourceAnalysisGateway(
            analysis = CoreSourceAnalysis(
                sourceUrl = "https://youtu.be/dQw4w9WgXcQ?token=$marker",
                title = "Sensitive source",
                durationMs = 4_000L,
                thumbnailUrl = null,
                qualityLabel = "720p",
                estimatedBytes = 10L,
                qualityOptions = listOf(CoreSourceQualityChoice("720p", 10L, "video-720p")),
                sourceProvider = "fixture",
                sourceMediaId = "https://example.test/id?token=$marker",
            ),
        )
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = source,
                downloadControlGateway = FakeDownloadControlGateway(),
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Sensitive source").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Canonical URL: https://youtu.be/dQw4w9WgXcQ?REDACTED").assertIsDisplayed()
        compose.onNodeWithText("Source: fixture / https://example.test/id?REDACTED").assertIsDisplayed()
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
    }

    @Test
    fun downloadSetupAndAdvancedOptionsRedactProviderLabelsWithoutChangingSelectionIds() {
        val marker = "RMD1802_PRIVATE_FORMAT_LABEL"
        val quality = "720p https://example.test/quality?token=$marker"
        val audio = "Audio https://example.test/audio?token=$marker"
        val subtitle = "English https://example.test/subtitle?token=$marker"
        val container = "mp4 https://example.test/container?token=$marker"
        val source = FakeSourceAnalysisGateway(
            analysis = CoreSourceAnalysis(
                sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
                title = "Sensitive provider options",
                durationMs = 42_000L,

                thumbnailUrl = null,
                qualityLabel = quality,
                estimatedBytes = 10L,
                qualityOptions = listOf(
                    CoreSourceQualityChoice(quality, 10L, "private-quality-id", separateAudio = true),
                ),
                subtitleOptions = listOf(
                    CoreSourceSubtitleChoice(
                        trackId = "private-subtitle-id",
                        language = "en",
                        label = subtitle,
                        format = "vtt",
                        autoGenerated = false,
                    ),
                ),
                audioOptions = listOf(
                    CoreSourceAudioChoice("private-audio-id", audio, "m4a", 128_000),
                    CoreSourceAudioChoice("other-audio-id", "Ordinary audio", "m4a", 160_000),
                ),
                containerOptions = listOf(container),
            ),
        )
        val controls = FakeDownloadControlGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = source,
                downloadControlGateway = controls,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Sensitive provider options").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Quality options: 720p https://example.test/quality?REDACTED")
            .assertExists()
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Select 720p https://example.test/quality?REDACTED")
            .assertExists()
        compose.onNodeWithText("Select Audio https://example.test/audio?REDACTED")
            .assertExists()
        compose.onNodeWithText("Select English https://example.test/subtitle?REDACTED")
            .assertExists()
        compose.onNodeWithText("Container paths").assertExists()
        compose.onNodeWithText("mp4 https://example.test/container?REDACTED")
            .assertExists()
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Apply options").performClick()
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(5_000) { controls.enqueuedChoiceIds.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("private-quality-id"), controls.enqueuedChoiceIds)
        }
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
    }

    @Test
    fun sourceWithNoExecutableQualityDoesNotOfferEnabledDownload() {
        val controls = FakeDownloadControlGateway()
        val source = FakeSourceAnalysisGateway(
            analysis = CoreSourceAnalysis(
                sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
                title = "No compatible format fixture",
                durationMs = 4_000L,
                thumbnailUrl = null,
                qualityLabel = "No compatible format",
                estimatedBytes = null,
                qualityOptions = emptyList(),
            ),
        )
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = source,
                downloadControlGateway = controls,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("No compatible format fixture").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Download setup").assertIsDisplayed()
        compose.onNodeWithText("Download").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(emptyList<String>(), controls.enqueuedJobIds) }
    }

    @Test
    fun downloadSetupGatewayFailureCannotExposeProviderCredentials() {
        val secret = "RMD1802_ENQUEUE_PRIVATE_TOKEN"
        val called = AtomicBoolean(false)
        val controls = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun enqueue(jobId: String, options: DownloadSelectionOptions): CoreGatewayResult<Boolean> {
                called.set(true)
                return CoreGatewayResult(
                    value = false,
                    error = CoreGatewayError(
                        kind = "fixture_failure",
                        message = "Authorization: Bearer $secret",
                        retryable = false,
                    ),
                )
            }
        }
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = FakeSourceAnalysisGateway(),
                downloadControlGateway = controls,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Fixture source").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Download").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            called.get() && runCatching {
                compose.onNodeWithText("Authorization: [REDACTED]").assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText(secret, substring = true).assertDoesNotExist()
    }
}
