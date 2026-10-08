package com.ekkus.offlineytplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.MimeTypes
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.AppLibraryDetailsGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryMutationGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreLibraryDetailAsset
import com.ekkus.offlineytplayer.coregateway.CoreLibraryDetails
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceAudioChoice
import com.ekkus.offlineytplayer.coregateway.CoreSourceSubtitleChoice
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import com.ekkus.offlineytplayer.playback.LocalAudioTrack
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalSubtitleTrack
import com.ekkus.offlineytplayer.settings.AppSettingsMutation
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
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
    fun library_details_action_reads_repository_detail_gateway_off_main_thread() {
        val details = RecordingLibraryDetailsGateway()
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { details },
            )
        }

        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { details.requestedIds.isNotEmpty() }
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("Fixture video · 720p · 0:42 · 1234 bytes · 1 asset").assertIsDisplayed()
            }.isSuccess
        }
        compose.runOnIdle { assertEquals(listOf("item-1"), details.requestedIds) }
    }


    @Test
    fun library_rename_and_remove_actions_use_repository_mutations_after_confirmation() {
        val mutations = RecordingLibraryMutationGateway()
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                mutationGatewayProvider = { mutations },
                libraryRootPath = "/data/user/0/com.ekkus.offlineytplayer/files",
            )
        }

        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Save rename").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { mutations.renamedIds.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("item-1"), mutations.renamedIds)
            assertEquals(listOf("Fixture video"), mutations.renamedTitles)
        }

        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Confirm remove").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { mutations.removedIds.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf("item-1"), mutations.removedIds)
            assertEquals(listOf(true), mutations.removeConfirmations)
        }
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
    fun paste_button_handles_missing_clipboard_text_gracefully() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("empty", ""))

        compose.setContent { OfflineYTPlayerApp() }

        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Paste").performClick()

        compose.onNodeWithText("Clipboard does not contain a video URL.").assertIsDisplayed()
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
        compose.setContent { OfflineYTPlayerApp() }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Downloads").performClick()
        compose.onNodeWithText("Default quality: Best compatible").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Playback").performClick()
        compose.onNodeWithText("Remember position").assertIsDisplayed()
    }
}

private fun AppSettingsSnapshot.updated(mutator: AppSettingsMutation.() -> Unit): AppSettingsSnapshot =
    AppSettingsMutation(this).apply(mutator).build()

private class FakeSourceAnalysisGateway(
    private val analysis: CoreSourceAnalysis = CoreSourceAnalysis(
        sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
        title = "Fixture source",
        durationMs = 42_000,
        thumbnailUrl = "https://example.test/thumb.jpg",
        qualityLabel = "720p",
        estimatedBytes = 12L * 1024L * 1024L,
        qualityOptions = listOf(
            CoreSourceQualityChoice("720p", 12L * 1024L * 1024L, "video-720p"),
            CoreSourceQualityChoice("Audio only", 2L * 1024L * 1024L, "audio-only"),
        ),
        subtitleOptions = listOf(
            CoreSourceSubtitleChoice(
                trackId = "subtitle-en",
                language = "en",
                label = "English",
                format = "vtt",
                autoGenerated = false,
            ),
        ),
        audioOptions = listOf(
            CoreSourceAudioChoice("audio-128", "128 kbps m4a", "m4a", 128_000),
            CoreSourceAudioChoice("audio-160", "160 kbps m4a", "m4a", 160_000),
        ),
        containerOptions = listOf("mp4", "webm"),
    ),
) : AppSourceAnalysisGateway {
    val analyzedUrls = mutableListOf<String>()

    override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
        analyzedUrls += sourceUrl
        return CoreGatewayResult(value = analysis, error = null)
    }

    override fun close() = Unit
}

private class RecordingLibraryDetailsGateway : AppLibraryDetailsGateway {
    val requestedIds = mutableListOf<String>()

    override fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?> {
        requestedIds += itemId
        return CoreGatewayResult(
            value = CoreLibraryDetails(
                itemId = itemId,
                provider = "fixture",
                mediaId = "fixture-1",
                canonicalUrl = null,
                displayTitle = "Fixture video",
                durationMs = 42_000,
                qualityLabel = "720p",
                completed = true,
                playbackPositionMs = 0,
                totalBytes = 1_234,
                assets = listOf(
                    CoreLibraryDetailAsset(
                        assetId = "video",
                        kind = "video",
                        relativePath = "media/fixture.mp4",
                        bytes = 1_234,
                        mimeType = "video/mp4",
                        hasSha256 = true,
                    ),
                ),
            ),
            error = null,
        )
    }

    override fun close() = Unit
}

private class RecordingLibraryMutationGateway : AppLibraryMutationGateway {
    val renamedIds = mutableListOf<String>()
    val renamedTitles = mutableListOf<String>()
    val removedIds = mutableListOf<String>()
    val removeConfirmations = mutableListOf<Boolean>()

    override fun renameDisplayTitle(itemId: String, displayTitle: String): CoreGatewayResult<String?> {
        renamedIds += itemId
        renamedTitles += displayTitle
        return CoreGatewayResult(value = displayTitle, error = null)
    }

    override fun removeLibraryItem(
        libraryRoot: String,
        itemId: String,
        confirmed: Boolean,
    ): CoreGatewayResult<Boolean> {
        removedIds += itemId
        removeConfirmations += confirmed
        return CoreGatewayResult(value = confirmed, error = null)
    }

    override fun close() = Unit

}
