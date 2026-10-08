package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Rmd1505StorageFailureInstrumentedTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = File(context.filesDir, DATABASE_NAME)

    @After
    fun cleanRuntimeState() {
        deleteRuntimeState()
    }

    @Test
    fun insufficientSpacePreflightFailsBeforeNetworkAndNeverCreatesCompletedLibraryItem() {
        deleteRuntimeState()
        val jobId = "rmd-1505-storage-preflight"
        val title = "RMD-1505 insufficient storage fixture"
        val relativePath = "items/rmd-1505-storage-preflight/video.mp4"
        val impossibleExpectedBytes = 64L * 1024L * 1024L * 1024L

        GeneratedUniffiDownloadControlGateway.open(database.absolutePath).use { control ->
            val result = control.enqueue(jobId)
            assertNull(result.error)
            assertEquals(true, result.value)
        }
        seedDurableWork(jobId, title, relativePath, impossibleExpectedBytes)

        assertTrue(
            "worker must claim the queued job and persist its permanent preflight failure",
            DownloadWorkerExecutor.execute(context, jobId),
        )

        GeneratedUniffiCoreGateway.open(database.absolutePath).use { core ->
            val queue = core.listDownloadQueue()
            assertNull(queue.error)
            val snapshot = requireNotNull(queue.value).single { it.jobId == jobId }
            assertEquals(CoreDownloadState.FAILED, snapshot.state)
            val error = assertNotNull(snapshot.lastError)
            assertFalse(error.retryable)
            assertTrue(error.message.contains("Not enough free storage"))

            val library = core.listLibrary()
            assertNull(library.error)
            assertFalse(requireNotNull(library.value).any { it.itemId == jobId && it.completed })
        }

        val finalAsset = File(context.filesDir, relativePath)
        val partialAsset = File(finalAsset.parentFile, "." + finalAsset.name + ".partial")
        assertFalse("preflight failure must not create a final asset", finalAsset.exists())
        assertFalse("preflight failure must occur before a partial write", partialAsset.exists())

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Downloads").performClick()
            compose.waitUntil(15_000) {
                runCatching { compose.onNodeWithText(title).assertIsDisplayed() }.isSuccess &&
                    runCatching { compose.onNodeWithText("Failed · 0%", substring = true).assertIsDisplayed() }.isSuccess &&
                    runCatching { compose.onNodeWithText("Error: Not enough free storage", substring = true).assertIsDisplayed() }.isSuccess
            }
        }
    }

    private fun seedDurableWork(jobId: String, title: String, relativePath: String, expectedBytes: Long) {
        val planJson = """
            {
              "source": {
                "provider": "direct-fixture",
                "media_id": "rmd-1505-storage-preflight",
                "canonical_url": "http://127.0.0.1:1/preflight-must-run-first"
              },
              "title": "$title",
              "quality": {
                "choice_id": "fixture-storage",
                "label": "Storage preflight",
                "estimated_bytes": $expectedBytes,
                "video_height": 720,
                "audio_only": false,
                "compatibility": "Preferred"
              },
              "assets": [
                {
                  "asset_id": "combined",
                  "kind": "Video",
                  "url": "http://127.0.0.1:1/preflight-must-run-first",
                  "relative_path": "$relativePath",
                  "expected_bytes": $expectedBytes,
                  "expected_sha256": null,
                  "mime_type": "video/mp4"
                }
              ]
            }
        """.trimIndent()

        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_work_items (
                  job_id TEXT PRIMARY KEY,
                  plan_json TEXT NOT NULL,
                  created_at_epoch_ms INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_presentations (
                  job_id TEXT PRIMARY KEY,
                  display_title TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT OR REPLACE INTO download_work_items(job_id, plan_json, created_at_epoch_ms) VALUES(?, ?, ?)",
                arrayOf<Any>(jobId, planJson, 1_700_000_000_000L),
            )
            db.execSQL(
                "INSERT OR REPLACE INTO download_presentations(job_id, display_title) VALUES(?, ?)",
                arrayOf<Any>(jobId, title),
            )
        }
    }

    private fun deleteRuntimeState() {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name == "items" || file.name.startsWith(DATABASE_NAME)) {
                file.deleteRecursively()
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "offline-yt-player.sqlite3"
    }
}
