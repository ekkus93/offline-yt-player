package com.ekkus.offlineytplayer.downloads

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.MainActivity
import com.ekkus.offlineytplayer.coregateway.CoreDownloadState
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGateway
import com.ekkus.offlineytplayer.coregateway.GeneratedUniffiDownloadControlGateway
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1507NotificationControlInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)
    private val notifications: NotificationManager = context.getSystemService(NotificationManager::class.java)

    @After
    fun cleanRuntimeState() {
        context.stopService(Intent(context, DownloadForegroundService::class.java))
        notifications.cancel(DownloadServicePolicy.NotificationId)
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(DATABASE_NAME)) file.deleteRecursively()
        }
    }

    @Test
    fun notificationActionsDriveDurableQueueAndProductionDownloadsUi() {
        cleanRuntimeState()
        val jobId = "rmd-1507-notification-control"
        val title = "RMD-1507 notification fixture"
        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val result = control.enqueue(jobId)
            assertNull(result.error)
            assertEquals(true, result.value)
        }
        persistPresentation(jobId, title)

        ContextCompat.startForegroundService(
            context,
            Intent(context, DownloadForegroundService::class.java)
                .setAction(DownloadForegroundService.ACTION_CONNECTIVITY_RETRY)
                .putExtra(DownloadForegroundService.EXTRA_QUEUE_ITEM_ID, jobId),
        )
        val notification = waitForDownloadNotification().notification
        val actions = notification.actions.associateBy { it.title.toString() }
        assertNotNull(actions["Pause"])
        assertNotNull(actions["Resume"])
        assertNotNull(actions["Cancel"])

        actions.getValue("Pause").actionIntent.send()
        waitForState(jobId, CoreDownloadState.PAUSED)

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Downloads").performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess &&
                    runCatching { compose.onNodeWithText("Paused · 0%", substring = true).assertIsDisplayed() }.isSuccess
            }

            actions.getValue("Resume").actionIntent.send()
            waitForState(jobId, CoreDownloadState.QUEUED)
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText("Active · 0%", substring = true).assertIsDisplayed() }.isSuccess
            }

            actions.getValue("Cancel").actionIntent.send()
            waitForState(jobId, CoreDownloadState.CANCELED)
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText("Canceled · 0%", substring = true).assertIsDisplayed() }.isSuccess
            }
        }
    }

    private fun waitForDownloadNotification(): android.service.notification.StatusBarNotification {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            notifications.activeNotifications.firstOrNull { it.id == DownloadServicePolicy.NotificationId }?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("download foreground notification did not become active")
    }

    private fun waitForState(jobId: String, expected: CoreDownloadState) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
                val queue = core.listDownloadQueue()
                assertNull(queue.error)
                queue.value?.firstOrNull { it.jobId == jobId }?.state
            }
            if (state == expected) return
            SystemClock.sleep(50)
        }
        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val state = core.listDownloadQueue().value?.firstOrNull { it.jobId == jobId }?.state
            assertEquals(expected, state)
        }
    }

    private fun persistPresentation(jobId: String, title: String) {
        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_presentations (
                  job_id TEXT PRIMARY KEY,
                  display_title TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT OR REPLACE INTO download_presentations(job_id, display_title) VALUES(?, ?)",
                arrayOf<Any>(jobId, title),
            )
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
    }
}
