package com.ekkus.offlineytplayer.ui

import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.os.Looper
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.MimeTypes
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import com.ekkus.offlineytplayer.playback.LocalAudioTrack
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalSubtitleTrack
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.ManagedCleanup
import com.ekkus.offlineytplayer.settings.ManagedStorageOperations
import com.ekkus.offlineytplayer.settings.ManagedStorageSummary
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test

class ProductionComposeBehaviorTest {
    @get:Rule
    val compose = createComposeRule()


    @Test
    fun storageSettingsScanAndCleanupDoNotBlockMainThreadOrDuplicateCleanup() {
        val scanStarted = CountDownLatch(1)
        val releaseScan = CountDownLatch(1)
        val cleanupStarted = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val scans = AtomicInteger(0)
        val cleanupCalls = AtomicInteger(0)
        val cleaned = AtomicBoolean(false)
        val allOperationsOnIo = AtomicBoolean(true)
        val requestedCleanup = AtomicReference<ManagedCleanup?>()
        val operations = object : ManagedStorageOperations {
            override fun summarize(): ManagedStorageSummary {
                if (Looper.myLooper() == Looper.getMainLooper()) allOperationsOnIo.set(false)
                if (scans.incrementAndGet() == 1) {
                    scanStarted.countDown()
                    check(releaseScan.await(30, TimeUnit.SECONDS)) { "Blocked summary never released" }
                }
                return ManagedStorageSummary(
                    mediaBytes = if (cleaned.get()) 200 else 100,
                    databaseBytes = 10,
                    partialBytes = 0,
                    cacheBytes = 0,
                    freeBytes = 1_024,
                )
            }

            override fun cleanup(action: ManagedCleanup): Long {
                if (Looper.myLooper() == Looper.getMainLooper()) allOperationsOnIo.set(false)
                requestedCleanup.set(action)
                cleanupCalls.incrementAndGet()
                cleanupStarted.countDown()
                check(releaseCleanup.await(30, TimeUnit.SECONDS)) { "Blocked cleanup never released" }
                cleaned.set(true)
                return 0
            }
        }
        compose.setContent { StorageSettingsPage(operations = operations) }
        try {
            assertTrue("Initial scan never started", scanStarted.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText("Loading storage…").assertIsDisplayed()
            compose.runOnIdle { assertTrue("Summary ran on UI thread", allOperationsOnIo.get()) }
            releaseScan.countDown()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithText("100 B").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Clear cache").performClick()
            compose.onNodeWithText("Confirm").performClick()
            assertTrue("Cleanup never started", cleanupStarted.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText("Cleaning storage…").assertIsDisplayed()
            compose.onNodeWithText("Confirm").assertIsNotEnabled()
            compose.onNodeWithText("Cancel").assertIsNotEnabled()
            compose.runOnIdle { assertTrue("Cleanup ran on UI thread", allOperationsOnIo.get()) }
            releaseCleanup.countDown()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithText("200 B").assertIsDisplayed() }.isSuccess
            }
            compose.onNodeWithText("Cleaning storage…").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(ManagedCleanup.Cache, requestedCleanup.get())
                assertEquals(1, cleanupCalls.get())
                assertEquals(2, scans.get())
                assertTrue(allOperationsOnIo.get())
            }
        } finally {
            releaseScan.countDown()
            releaseCleanup.countDown()
        }
    }

    @Test
    fun library_empty_and_populated_states_are_behavioral() {
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(emptyList()),
            
                onUpdateSettings = {},
                onQueryChanged = {},
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
            
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Fixture video").assertIsDisplayed()
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithText("Library details are unavailable for Fixture video.").assertIsDisplayed()
        compose.onNodeWithText("Fixture video: 720p · 0:42").assertDoesNotExist()
        compose.onNodeWithText("Library details").assertDoesNotExist()
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
            OfflineYTPlayerApp(initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
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

        compose.setContent { OfflineYTPlayerApp(
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            ) }

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Paste").performClick()

        compose.onNodeWithText("https://youtu.be/dQw4w9WgXcQ").assertIsDisplayed()
        compose.onNodeWithText("Pasted clipboard text. Choose Analyze to validate it.").assertIsDisplayed()
    }

    @Test
    fun paste_button_handles_missing_clipboard_text_gracefully() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("empty", ""))

        compose.setContent { OfflineYTPlayerApp(
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            ) }

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Paste").performClick()

        compose.onNodeWithText("Clipboard does not contain a video URL.").assertIsDisplayed()
    }

    @Test
    fun player_entry_exposes_transport_and_track_controls() {
        compose.setContent {
            OfflineYTPlayerApp(
                libraryState = LibraryScreenState.Ready(
                    listOf(
                        LibraryRowModel(
                            id = "item-playback",
                            title = "Fixture playable",
                            detail = "720p · 0:42",
                            videoPath = "/data/local/tmp/fixture-video.mp4",
                            audioPath = "/data/local/tmp/fixture-audio.m4a",
                            resumePositionMs = 12_000,
                        ),
                    ),
                ),
            
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            )
        }

        compose.onNodeWithText("Fixture playable").assertIsDisplayed()
        compose.onNodeWithText("Play").performClick()
        compose.onNodeWithText("Fixture playable").assertIsDisplayed()
        compose.onNodeWithText("-10s").assertIsDisplayed()
        compose.onNodeWithText("Play/Pause").assertIsDisplayed()
        compose.onNodeWithText("+10s").assertIsDisplayed()
        compose.onNodeWithText("Speed 1.0×").assertIsDisplayed()
        compose.onNodeWithText("Subtitles").assertIsDisplayed()
        compose.onNodeWithText("Audio").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Fixture playable").assertIsDisplayed()
    }

    @Test
    fun player_screen_exposes_subtitle_and_audio_track_labels() {
        compose.setContent {

            PortraitPlayerScreen(
                asset = LocalPlaybackAsset(
                    videoPath = "/data/local/tmp/fixture-video.mp4",
                    audioPath = "/data/local/tmp/fixture-audio.m4a",
                    title = "Fixture track controls",
                    subtitleTracks = listOf(
                        LocalSubtitleTrack(
                            path = "/data/local/tmp/subtitles.vtt",
                            language = "en",
                            label = "English",
                            mimeType = MimeTypes.TEXT_VTT,
                        ),
                    ),
                    audioTracks = listOf(
                        LocalAudioTrack("/data/local/tmp/fixture-audio-en.m4a", language = "en", label = "Main"),
                        LocalAudioTrack("/data/local/tmp/fixture-audio-es.m4a", language = "es", label = "Spanish"),
                    ),
                ),
                settings = AppSettingsSnapshot(),
                onUpdateSettings = {},
                onBack = {},
            )
        }

        compose.onNodeWithText("Fixture track controls").assertIsDisplayed()
        compose.onNodeWithText("Subtitles: English").assertIsDisplayed()
        compose.onNodeWithText("Audio: Main").assertIsDisplayed()
    }

    @Test
    fun settings_interactions_update_runtime_snapshot() {
        compose.setContent {

            var settings by remember { mutableStateOf(AppSettingsSnapshot()) }
            OfflineYTPlayerApp(
                settingsSnapshot = settings,
                onUpdateSettings = { mutation -> settings = settings.updated(mutation) },
            
                onLibraryQueryChanged = {},
            )
        }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("Default quality: Best compatible").performClick()
        compose.onNodeWithText("Default quality: Audio only").assertIsDisplayed()
        compose.onNodeWithText("Concurrent downloads: 2").performClick()
        compose.onNodeWithText("Concurrent downloads: 3").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Playback").performClick()
        compose.onNodeWithText("Default speed: 1.0×").performClick()
        compose.onNodeWithText("Default speed: 1.25×").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Theme: System").performClick()
        compose.onNodeWithText("Theme: Light").assertIsDisplayed()
        compose.onNodeWithText("Library layout: List").performClick()
        compose.onNodeWithText("Library layout: Grid").assertIsDisplayed()
    }

    @Test
    fun settings_hub_navigation_exposes_operational_pages() {
        compose.setContent { OfflineYTPlayerApp(
                onLibraryQueryChanged = {},
                onUpdateSettings = {},
            ) }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("Default quality: Best compatible").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Playback").performClick()
        compose.onNodeWithText("Remember position").assertIsDisplayed()
    }
}
