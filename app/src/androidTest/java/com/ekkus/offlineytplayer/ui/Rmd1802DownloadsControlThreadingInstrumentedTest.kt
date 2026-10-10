package com.ekkus.offlineytplayer.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1802DownloadsControlThreadingInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleDownloadsPauseInvokesControlOffMainAndReportsDurableUpdate() {
        val calledOffMain = AtomicBoolean(false)
        val gateway = object : AppDownloadControlGateway by FakeDownloadControlGateway() {
            override fun pause(jobId: String): CoreGatewayResult<Boolean> {
                check(Looper.myLooper() != Looper.getMainLooper()) {
                    "Blocking download controls must not execute on Compose main thread"
                }
                check(jobId == "job-1")
                calledOffMain.set(true)
                return CoreGatewayResult(true, null)
            }
        }
        compose.setContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(DownloadRowModel("job-1", "Fixture download", DownloadUiState.Active, 20, "20 / 100 bytes")),
                ),
                controlGateway = gateway,
            )
        }
        compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(10_000) {
            calledOffMain.get() && runCatching {
                compose.onNodeWithText("Pause requested for Fixture download.").assertIsDisplayed()
            }.isSuccess
        }
        assertTrue(calledOffMain.get())
    }
}
