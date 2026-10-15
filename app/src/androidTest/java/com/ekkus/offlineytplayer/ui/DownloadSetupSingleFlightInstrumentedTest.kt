package com.ekkus.offlineytplayer.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.coregateway.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadSetupSingleFlightInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun enqueueCannotBeSubmittedTwiceWhileFirstRequestIsPending() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val source = object : AppSourceAnalysisGateway {
            override fun analyze(sourceUrl: String) = CoreGatewayResult(
                CoreSourceAnalysis(
                    sourceUrl = url, title = "Single flight fixture",
                    durationMs = 42_000L, thumbnailUrl = null,
                    qualityLabel = "720p", estimatedBytes = 1024L,
                    qualityOptions = listOf(CoreSourceQualityChoice("720p", 1024L, "video-720")),
                ), null,
            )
            override fun close() = Unit
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val controls = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun enqueue(jobId: String, options: DownloadSelectionOptions): CoreGatewayResult<Boolean> {
                check(Looper.myLooper() != Looper.getMainLooper()) { "Enqueue ran on UI thread" }
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(30, TimeUnit.SECONDS)) { "Enqueue was not released" }
                return CoreGatewayResult(true, null)
            }
        }
        compose.setContent {
            OfflineYTPlayerApp(
                initialSharedUrl = url, sourceAnalysisGateway = source,
                downloadControlGateway = controls,
                onLibraryQueryChanged = {}, onUpdateSettings = {},
            )
        }
        try {
            compose.onNodeWithText("Analyze").performClick()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithText("Single flight fixture").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Download").performClick()
            assertTrue("Enqueue never started", entered.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText("Download").assertIsNotEnabled()
            compose.onNodeWithText("Options").assertIsNotEnabled()
            compose.onNodeWithText("Paste").assertIsNotEnabled()
            compose.onNodeWithText("Video URL").assertIsNotEnabled()
            compose.runOnIdle { assertEquals(1, calls.get()) }
            release.countDown()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithText("Download scheduled", substring = true).assertIsDisplayed() }.isSuccess
            }
            compose.runOnIdle { assertEquals(1, calls.get()) }
        } finally {
            release.countDown()
        }
    }
}
