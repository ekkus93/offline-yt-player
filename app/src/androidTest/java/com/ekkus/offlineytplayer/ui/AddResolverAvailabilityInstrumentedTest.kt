package com.ekkus.offlineytplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import java.util.concurrent.CopyOnWriteArrayList
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
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
        // The same startup diagnostic may appear in both the Add-screen status
        // and its actionable result panel. Require a visible match, not uniqueness.
        compose.onAllNodesWithText(diagnostic)[0].assertIsDisplayed()
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

    @Test
    fun secondAnalyzeTapIsBlockedWhileSourceResolutionIsInFlight() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val analyzeCalls = AtomicInteger()
        val source = object : AppSourceAnalysisGateway {
            override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
                analyzeCalls.incrementAndGet()
                started.countDown()
                check(release.await(15, TimeUnit.SECONDS)) {
                    "Controlled source resolution was not released"
                }
                return CoreGatewayResult(
                    value = CoreSourceAnalysis(
                        sourceUrl = sourceUrl,
                        title = "Controlled Analyze fixture",
                        durationMs = 10_000,
                        thumbnailUrl = null,
                        qualityLabel = "720p",
                        estimatedBytes = 42,
                        qualityOptions = listOf(
                            CoreSourceQualityChoice(
                                label = "720p",
                                estimatedBytes = 42,
                                choiceId = "format:fixture",
                                separateAudio = false,
                            ),
                        ),
                        subtitleOptions = emptyList(),
                        audioOptions = emptyList(),
                        containerOptions = listOf("mp4"),
                    ),
                    error = null,
                )
            }

            override fun close() = Unit
        }
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://www.youtube.com/watch?v=AACwyjlk8ko&t=5s",
                sourceAnalysisGateway = source,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        try {
            compose.onNodeWithText("Analyze").assertIsEnabled().performClick()
            assertTrue("Analyze never entered the source gateway", started.await(10, TimeUnit.SECONDS))
            // This UI replaces Analyze during the in-flight request rather than disabling it.
            // There is no second tappable Analyze action until resolution completes.
            compose.onNodeWithText("Analyze").assertDoesNotExist()
            compose.runOnIdle { assertEquals(1, analyzeCalls.get()) }
        } finally {
            release.countDown()
        }
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText("Controlled Analyze fixture", substring = true)
                    .assertIsDisplayed()
            }.isSuccess
        }
        compose.runOnIdle { assertEquals(1, analyzeCalls.get()) }
    }

    @Test
    fun plainWatchUrlFromShareOpensRealAddAnalyzeFlow() {
        analyzeSharedWatchUrlAndRequireCanonical(
            "https://www.youtube.com/watch?v=AACwyjlk8ko",
        )
    }

    @Test
    fun watchShareLinkDropsTrackingAndTimeParametersBeforeAnalysis() {
        analyzeSharedWatchUrlAndRequireCanonical(
            "https://www.youtube.com/watch?feature=share&t=43s&v=AACwyjlk8ko&si=PRIVATE_TRACKING_TOKEN",
        )
    }

    private fun analyzeSharedWatchUrlAndRequireCanonical(sharedUrl: String) {
        val source = FakeSourceAnalysisGateway()
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = sharedUrl,
                sourceAnalysisGateway = source,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { source.analyzedUrls.size == 1 }
        compose.runOnIdle {
            assertEquals(
                listOf("https://www.youtube.com/watch?v=AACwyjlk8ko"),
                source.analyzedUrls,
            )
        }
        compose.onNodeWithText("PRIVATE_TRACKING_TOKEN", substring = true).assertDoesNotExist()
    }

    @Test
    fun typedNetworkErrorIsRedactedAndSecondAnalyzeRecoversWithoutDuplicateRequests() {
        val calls = AtomicInteger(0)
        val analyzedUrls = CopyOnWriteArrayList<String>()
        val source = object : AppSourceAnalysisGateway {
            override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
                analyzedUrls.add(sourceUrl)
                if (calls.incrementAndGet() == 1) {
                    return CoreGatewayResult(
                        value = null,
                        error = CoreGatewayError(
                            kind = "NETWORK_TIMEOUT",
                            message = "Authorization: Bearer PRIVATE_ANALYZE_TOKEN, " +
                                "https://cdn.invalid/file?sig=PRIVATE_SIGNED_QUERY",
                            retryable = true,
                        ),
                    )
                }
                return CoreGatewayResult(
                    value = CoreSourceAnalysis(
                        sourceUrl = sourceUrl,
                        title = "Recovered source fixture",
                        durationMs = 61_000,
                        thumbnailUrl = null,
                        qualityLabel = "720p",
                        estimatedBytes = 1_000,
                        qualityOptions = listOf(
                            CoreSourceQualityChoice(
                                label = "720p",
                                estimatedBytes = 1_000,
                                choiceId = "format:fixture-720p",
                            ),
                        ),
                    ),
                    error = null,
                )
            }

            override fun close() = Unit
        }
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/AACwyjlk8ko?t=11s",
                sourceAnalysisGateway = source,
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }
        compose.onNodeWithText("Analyze").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText("Source network request failed. Try again.")
                    .assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText("PRIVATE_ANALYZE_TOKEN", substring = true).assertDoesNotExist()
        compose.onNodeWithText("PRIVATE_SIGNED_QUERY", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Analyze").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText("Recovered source fixture").assertExists()
            }.isSuccess
        }
        compose.onNodeWithText("Source network request failed. Try again.").assertDoesNotExist()
        compose.onNodeWithText("PRIVATE_ANALYZE_TOKEN", substring = true).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(2, calls.get())
            assertEquals(
                listOf(
                    "https://www.youtube.com/watch?v=AACwyjlk8ko",
                    "https://www.youtube.com/watch?v=AACwyjlk8ko",
                ),
                analyzedUrls.toList(),
            )
        }
    }
}
