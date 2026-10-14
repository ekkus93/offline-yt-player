package com.ekkus.offlineytplayer.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.settings.ManagedCleanup
import com.ekkus.offlineytplayer.settings.ManagedStorageOperations
import com.ekkus.offlineytplayer.settings.ManagedStorageSummary
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageSettingsRecoveryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialScanFailureCanRetryWithoutLeavingSettings() {
        val scanCount = AtomicInteger(0)
        val offMain = AtomicBoolean(true)
        val operations = object : ManagedStorageOperations {
            override fun summarize(): ManagedStorageSummary {
                if (Looper.myLooper() == Looper.getMainLooper()) offMain.set(false)
                if (scanCount.incrementAndGet() == 1) throw IOException("temporary filesystem fault")
                return ManagedStorageSummary(
                    mediaBytes = 128,
                    databaseBytes = 16,
                    partialBytes = 0,
                    cacheBytes = 0,
                    freeBytes = 2048,
                )
            }

            override fun cleanup(action: ManagedCleanup): Long {
                throw AssertionError("Cleanup must not run during scan recovery")
            }
        }
        compose.setContent { StorageSettingsPage(operations = operations) }
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText("Storage details unavailable.").assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText("Clear cache").assertIsNotEnabled()
        compose.onNodeWithText("Clean incomplete").assertIsNotEnabled()
        compose.onNodeWithText("Retry storage scan").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText("128 B").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Storage details unavailable.").assertDoesNotExist()
        compose.onNodeWithText("Retry storage scan").assertDoesNotExist()
        compose.onNodeWithText("Clear cache").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(2, scanCount.get())
            assertTrue("Storage scans must execute off the Android main thread", offMain.get())
        }
    }
    @Test fun cleanupRefreshFailureInvalidatesStaleSummaryAndCanRetry() {
        val scans = AtomicInteger(0)
        val cleanups = AtomicInteger(0)
        val offMain = AtomicBoolean(true)
        val operations = object : ManagedStorageOperations {
            override fun summarize(): ManagedStorageSummary {
                if (Looper.myLooper() == Looper.getMainLooper()) offMain.set(false)
                val attempt = scans.incrementAndGet()
                if (attempt == 2) throw IOException("post-cleanup storage scan failed")
                return ManagedStorageSummary(
                    mediaBytes = if (attempt == 1) 100 else 200,
                    databaseBytes = 16,
                    partialBytes = 0,
                    cacheBytes = 0,
                    freeBytes = 2048,
                )
            }
            override fun cleanup(action: ManagedCleanup): Long {
                if (Looper.myLooper() == Looper.getMainLooper()) offMain.set(false)
                assertEquals(ManagedCleanup.Cache, action)
                cleanups.incrementAndGet()
                return 0
            }
        }
        compose.setContent { StorageSettingsPage(operations = operations) }
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText("100 B").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Clear cache").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText("Retry storage scan").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("100 B").assertDoesNotExist()
        compose.onNodeWithText("Clear cache").assertIsNotEnabled()
        compose.onNodeWithText("Clean incomplete").assertIsNotEnabled()
        compose.onNodeWithText("Storage details unavailable.").assertIsDisplayed()
        compose.onNodeWithText("Retry storage scan").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText("200 B").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Storage details unavailable.").assertDoesNotExist()
        compose.onNodeWithText("Clear cache").assertIsEnabled()
        compose.runOnIdle {
            assertEquals(3, scans.get())
            assertEquals(1, cleanups.get())
            assertTrue("Storage operations must run off main", offMain.get())
        }
    }

}
