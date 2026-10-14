package com.ekkus.offlineytplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertTrue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.media3.common.MimeTypes
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.AppLibraryDetailsGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryMutationGateway
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.AppDownloadControlGateway
import com.ekkus.offlineytplayer.coregateway.DownloadSelectionOptions
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
            
                onUpdateSettings = {},
                onQueryChanged = {},
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
    fun libraryRenameSuccessDoesNotExposeUntrustedReturnedTitle() {
        val credential = "RMD1802_PRIVATE_RENAME_CREDENTIAL"
        val mutations = object : AppLibraryMutationGateway {
            override fun renameDisplayTitle(itemId: String, displayTitle: String): CoreGatewayResult<String?> =
                CoreGatewayResult(
                    value = "Renamed https://example.test/title?token=$credential",
                    error = null,
                )

            override fun removeLibraryItem(
                libraryRoot: String,
                itemId: String,
                confirmed: Boolean,
            ): CoreGatewayResult<Boolean> = CoreGatewayResult(false, null)

            override fun close() = Unit
        }
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                mutationGatewayProvider = { mutations },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Save rename").performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithText("Renamed to Renamed https://example.test/title?REDACTED.")
                    .assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText(credential, substring = true).assertDoesNotExist()
    }


    @Test
    fun libraryRenameInvalidatesExpandedDetails() {
        val details = RecordingLibraryDetailsGateway()
        val mutations = RecordingLibraryMutationGateway()
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { details },
                mutationGatewayProvider = { mutations },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Library details").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Save rename").performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithText("Renamed to Fixture video.").assertIsDisplayed()
                compose.onNodeWithText("Library details").assertDoesNotExist()
            }.isSuccess
        }
        compose.runOnIdle {
            assertEquals(listOf("item-1"), mutations.renamedIds)
            assertEquals(listOf("Fixture video"), mutations.renamedTitles)
        }
    }

    @Test
    fun libraryRemovalClearsExpandedDetailsAndInvalidatesStaleSelection() {
        val details = RecordingLibraryDetailsGateway()
        val mutations = RecordingLibraryMutationGateway()
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { details },
                mutationGatewayProvider = { mutations },
                libraryRootPath = "/tmp/fixture-library",
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Library details").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Confirm remove").performClick()
        compose.waitUntil(5_000) {
            mutations.removedIds.contains("item-1") &&
                runCatching { compose.onNodeWithText("Library details").assertDoesNotExist() }.isSuccess
        }
        compose.runOnIdle {
            assertEquals(listOf("item-1"), mutations.removedIds)
            assertEquals(listOf(true), mutations.removeConfirmations)
        }
    }

    @Test
    fun libraryDetailsRejectsGatewayRecordForDifferentItem() {
        val gateway = RecordingLibraryDetailsGateway(
            overriddenDetails = CoreLibraryDetails(
                itemId = "another-private-item",
                provider = "fixture",
                mediaId = "other-id",
                canonicalUrl = null,
                displayTitle = "Different private video",
                durationMs = 999_000,
                qualityLabel = "4K",
                completed = true,
                playbackPositionMs = 0,
                totalBytes = 654_321,
                assets = emptyList(),
            ),
        )
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { gateway },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithText("Library details are unavailable for Fixture video.")
                    .assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText("Different private video").assertDoesNotExist()
        compose.onNodeWithText("Library details").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("item-1"), gateway.requestedIds) }
    }


    @Test
    fun libraryDetailsDoesNotPublishSupersededGatewayResponse() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstReturned = CountDownLatch(1)
        val gateway = object : AppLibraryDetailsGateway {
            override fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?> {
                if (itemId == "item-1") {
                    firstStarted.countDown()
                    check(releaseFirst.await(10, TimeUnit.SECONDS)) { "First details request was not released" }
                }
                val details = CoreLibraryDetails(
                    itemId = itemId,
                    provider = "fixture",
                    mediaId = itemId,
                    canonicalUrl = null,
                    displayTitle = if (itemId == "item-1") "Stale gateway metadata" else "Current gateway metadata",
                    durationMs = 42_000,
                    qualityLabel = "720p",
                    completed = true,
                    playbackPositionMs = 0,
                    totalBytes = 1_234,
                    assets = emptyList(),
                )
                if (itemId == "item-1") firstReturned.countDown()
                return CoreGatewayResult(value = details, error = null)
            }

            override fun close() = Unit
        }
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(
                        LibraryRowModel("item-1", "First row", "first fixture"),
                        LibraryRowModel("item-2", "Second row", "second fixture"),
                    ),
                ),
                detailsGatewayProvider = { gateway },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        try {
            compose.onAllNodesWithText("Details")[0].performClick()
            assertTrue("First details call never started", firstStarted.await(5, TimeUnit.SECONDS))
            compose.onAllNodesWithText("Details")[1].performClick()
            compose.waitUntil(5_000) {
                runCatching { compose.onNodeWithText("Current gateway metadata").assertIsDisplayed() }.isSuccess
            }
            releaseFirst.countDown()
            assertTrue("First details call never returned", firstReturned.await(5, TimeUnit.SECONDS))
            compose.waitForIdle()
            compose.onNodeWithText("Current gateway metadata").assertIsDisplayed()
            compose.onNodeWithText("Stale gateway metadata").assertDoesNotExist()
        } finally {
            releaseFirst.countDown()
        }
    }

    @Test
    fun libraryRepositoryChangeDropsDelayedDetailsAndRenameControls() {
        val requested = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val gateway = object : AppLibraryDetailsGateway {
            override fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?> {
                requested.countDown()
                check(unblock.await(30, TimeUnit.SECONDS))
                returned.countDown()
                return CoreGatewayResult(CoreLibraryDetails(
                    itemId = itemId, provider = "fixture", mediaId = itemId,
                    canonicalUrl = null, displayTitle = "Private stale metadata",
                    durationMs = 42_000, qualityLabel = "720p", completed = true,
                    playbackPositionMs = 0, totalBytes = 1234, assets = emptyList(),
                ), null)
            }
            override fun close() = Unit
        }
        var repository by mutableStateOf<LibraryScreenState>(LibraryScreenState.Ready(
            listOf(LibraryRowModel("old", "Old video", "720p")),
        ))
        compose.setContent {
            LibraryScreen(onAdd = {}, onPlay = {}, state = repository,
                detailsGatewayProvider = { gateway }, onUpdateSettings = {}, onQueryChanged = {})
        }
        try {
            compose.onNodeWithText("Details").performClick()
            assertTrue(requested.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("Rename").performClick()
            compose.onNodeWithText("Cancel rename").assertIsDisplayed()
            compose.runOnIdle { repository = LibraryScreenState.Unavailable("Repository disconnected") }
            compose.onNodeWithText("Repository disconnected").assertIsDisplayed()
            compose.onNodeWithText("Cancel rename").assertDoesNotExist()
            compose.onNodeWithText("Save rename").assertDoesNotExist()
            compose.onNodeWithText("Library details").assertDoesNotExist()
            compose.runOnIdle { repository = LibraryScreenState.Ready(
                listOf(LibraryRowModel("new", "New video", "1080p")),
            ) }
            compose.onNodeWithText("New video").assertIsDisplayed()
            unblock.countDown()
            assertTrue(returned.await(5, TimeUnit.SECONDS))
            compose.waitForIdle()
            compose.onNodeWithText("Private stale metadata").assertDoesNotExist()
            compose.onNodeWithText("Library details").assertDoesNotExist()
            compose.onNodeWithText("Loading details for Old video…").assertDoesNotExist()
        } finally { unblock.countDown() }
    }

    @Test
    fun libraryRowRemovalFromReadyClearsOpenDetailsAndRemoveControls() {
        var repository by mutableStateOf<LibraryScreenState>(LibraryScreenState.Ready(
            listOf(LibraryRowModel("old", "Old video", "720p")),
        ))
        val gateway = RecordingLibraryDetailsGateway()
        compose.setContent {
            LibraryScreen(onAdd = {}, onPlay = {}, state = repository,
                detailsGatewayProvider = { gateway }, onUpdateSettings = {}, onQueryChanged = {})
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Library details").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Confirm remove").assertIsDisplayed()
        compose.runOnIdle { repository = LibraryScreenState.Ready(
            listOf(LibraryRowModel("new", "New video", "1080p")),
        ) }
        compose.onNodeWithText("New video").assertIsDisplayed()
        compose.onNodeWithText("Library details").assertDoesNotExist()
        compose.onNodeWithText("Confirm remove").assertDoesNotExist()
        compose.onNodeWithText("Cancel remove").assertDoesNotExist()
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
            
                onUpdateSettings = {},
                onQueryChanged = {},
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

    @Test
    fun libraryDetailsSummaryBoundsUntrustedQualityLabel() {
        val marker = "X".repeat(400)
        val details = RecordingLibraryDetailsGateway(qualityLabel = marker)
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { details },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        val bounded = "X".repeat(128)
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("Fixture video · $bounded · 0:42 · 1234 bytes · 1 asset")
                    .assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
    }

    @Test
    fun libraryDetailsPanelRedactsPersistedUrlsAndAssetPaths() {
        val secret = "RMD1802_PRIVATE_LIBRARY_DETAILS_TOKEN"
        val details = RecordingLibraryDetailsGateway(
            overriddenDetails = CoreLibraryDetails(
                itemId = "item-1",
                provider = "fixture",
                mediaId = "provider-1",
                canonicalUrl = "https://example.test/watch?v=42&token=$secret",
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
                        relativePath = "https://example.test/asset?token=$secret",
                        bytes = 1_234,
                        mimeType = "video/mp4",
                        hasSha256 = true,
                    ),
                ),
            ),
        )
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { details },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("Canonical source: https://example.test/watch?REDACTED")
                    .assertExists()
            }.isSuccess
        }
        compose.onNodeWithText(
            "Asset: video · https://example.test/asset?REDACTED · 1234 bytes · video/mp4 · SHA-256 recorded",
        ).assertExists()
        compose.onNodeWithText(secret, substring = true).assertDoesNotExist()
    }

    @Test
    fun recoveryExportUsesRedactedDiagnosticInsteadOfRawGatewayText() {
        val secret = "RMD1802_PRIVATE_RECOVERY_CREDENTIAL"
        var shareChooser: Intent? = null
        val interceptingContext = object : ContextWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ) {
            override fun startActivity(intent: Intent) {
                shareChooser = intent
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides interceptingContext) {
                LibraryScreen(
                    onAdd = {},
                    onPlay = {},
                    state = LibraryScreenState.Failed("database corrupt. Authorization: Bearer $secret"),
                    onUpdateSettings = {},
                    onQueryChanged = {},
                )
            }
        }
        compose.onNodeWithText("Export diagnostics").performClick()
        compose.runOnIdle {
            val chooser = checkNotNull(shareChooser)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            val shared = checkNotNull(send)
            assertEquals(Intent.ACTION_SEND, shared.action)
            val payload = shared.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            assertTrue("Recovery export must redact credentials: $payload", "Authorization: [REDACTED]" in payload)
            assertTrue("Recovery export must not contain raw credentials", secret !in payload)
        }
    }

    @Test
    fun downloadsAndLibraryUnavailableDiagnosticsAreRedactedInRenderedUi() {
        val secret = "RMD1802_PRIVATE_UI_CREDENTIAL"
        compose.setContent {
            DownloadsScreen(state = DownloadsScreenState.Failed("Authorization: Bearer $secret"))
        }
        compose.onNodeWithText("Downloads repository failed: Authorization: [REDACTED]").assertIsDisplayed()
        compose.onNodeWithText(secret, substring = true).assertDoesNotExist()

        compose.setContent {
            DownloadsScreen(state = DownloadsScreenState.Ready(listOf(
                DownloadRowModel(
                    id = "failed",
                    title = "Fixture failed download",
                    state = DownloadUiState.Failed,
                    percent = 25,
                    size = "25 / 100 bytes",
                    error = "Authorization: Bearer $secret",
                ),
            )))
        }
        compose.onNodeWithText("Error: Authorization: [REDACTED]").assertIsDisplayed()
        compose.onNodeWithText(secret, substring = true).assertDoesNotExist()

        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Unavailable("Authorization: Bearer $secret"),
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Authorization: [REDACTED]").assertIsDisplayed()
        compose.onNodeWithText(secret, substring = true).assertDoesNotExist()
    }

    @Test
    fun unknownLengthDownloadsRenderActualBytesWithoutInventingZeroPercent() {
        compose.setContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "unknown-length-job",
                            title = "Unknown-length fixture",
                            state = DownloadUiState.Active,
                            percent = null,
                            size = "2048 bytes",
                        ),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Unknown-length fixture").assertIsDisplayed()
        compose.onNodeWithText("Progress unknown", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2048 bytes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("0%", substring = true).assertDoesNotExist()
    }

    @Test
    fun libraryAndDownloadRowsRedactPersistedProviderMetadataInRealCompose() {
        val marker = "RMD1802_ROW_PRIVATE_TOKEN"
        val signedUrl = "https://cdn.example/media?sig=$marker"
        val sanitizedUrl = "https://cdn.example/media?REDACTED"

        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Library $signedUrl", "Details $signedUrl")),
                ),
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Library $sanitizedUrl").assertIsDisplayed()
        compose.onNodeWithText("Details $sanitizedUrl").assertIsDisplayed()
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()

        val controls = FakeDownloadControlGateway()
        compose.setContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "job-1",
                            title = "Download $signedUrl",
                            state = DownloadUiState.Active,
                            percent = 25,
                            size = "Size $signedUrl",
                            stateLabel = "Status $signedUrl",
                            speedLabel = "Speed $signedUrl",
                            etaLabel = "ETA $signedUrl",
                        ),
                    ),
                ),
                controlGateway = controls,
            )
        }
        compose.onNodeWithText("Download $sanitizedUrl").assertIsDisplayed()
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
        compose.onNodeWithText("Pause").performClick()
        compose.runOnIdle { assertEquals(listOf("job-1"), controls.pausedJobIds) }
        compose.onNodeWithText(marker, substring = true).assertDoesNotExist()
    }

    @Test
    fun libraryDetailsWithManyAssetsKeepsCloseActionScrollableAndReachable() {
        val details = RecordingLibraryDetailsGateway(
            overriddenDetails = CoreLibraryDetails(
                itemId = "item-1",
                provider = "fixture",
                mediaId = "large-asset-list",
                canonicalUrl = null,
                displayTitle = "Fixture video",
                durationMs = 42_000,
                qualityLabel = "720p",
                completed = true,
                playbackPositionMs = 0,
                totalBytes = 100_000,
                assets = (0 until 80).map { index ->
                    CoreLibraryDetailAsset(
                        assetId = "asset-$index",
                        kind = "video",
                        relativePath = "media/fixture-$index.mp4",
                        bytes = 1_000,
                        mimeType = "video/mp4",
                        hasSha256 = true,
                    )
                },
            ),
        )
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("item-1", "Fixture video", "720p · 0:42")),
                ),
                detailsGatewayProvider = { details },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { compose.onNodeWithText("Library details").assertExists() }.isSuccess
        }
        compose.onNodeWithText("Showing the first 64 of 80 recorded assets.").assertExists()
        compose.onNodeWithText("Close details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close details").performClick()
        compose.onNodeWithText("Library details").assertDoesNotExist()
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

private class RecordingLibraryDetailsGateway(
    private val qualityLabel: String = "720p",
    private val overriddenDetails: CoreLibraryDetails? = null,
) : AppLibraryDetailsGateway {
    val requestedIds = mutableListOf<String>()

    override fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?> {
        requestedIds += itemId
        return CoreGatewayResult(
            value = overriddenDetails ?: CoreLibraryDetails(
                itemId = itemId,
                provider = "fixture",
                mediaId = "fixture-1",
                canonicalUrl = null,
                displayTitle = "Fixture video",
                durationMs = 42_000,
                qualityLabel = qualityLabel,
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
