package com.ekkus.offlineytplayer.ui

import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ekkus.offlineytplayer.coregateway.AppLibraryDetailsGateway
import com.ekkus.offlineytplayer.coregateway.AppLibraryMutationGateway
import com.ekkus.offlineytplayer.coregateway.CoreLibraryDetails
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

class ProductionLibraryDetailsBehaviorTest {
    @get:Rule
    val compose = createComposeRule()


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
    fun libraryContentRefreshWithStableIdDropsInFlightDetails() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val gateway = object : AppLibraryDetailsGateway {
            override fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?> {
                started.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Details lookup was not released" }
                returned.countDown()
                return CoreGatewayResult(
                    CoreLibraryDetails(
                        itemId = itemId,
                        provider = "fixture",
                        mediaId = itemId,
                        canonicalUrl = null,
                        displayTitle = "Obsolete repository details",
                        durationMs = 42_000,
                        qualityLabel = "720p",
                        completed = true,
                        playbackPositionMs = 0,
                        totalBytes = 1_234,
                        assets = emptyList(),
                    ),
                    null,
                )
            }
            override fun close() = Unit
        }
        var repository by mutableStateOf<LibraryScreenState>(
            LibraryScreenState.Ready(listOf(LibraryRowModel("stable-id", "Before refresh", "720p"))),
        )
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = repository,
                detailsGatewayProvider = { gateway },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        try {
            compose.onNodeWithText("Details").performClick()
            assertTrue("Details lookup did not start", started.await(5, TimeUnit.SECONDS))
            compose.runOnIdle {
                repository = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("stable-id", "After refresh", "1080p")),
                )
            }
            compose.onNodeWithText("After refresh").assertIsDisplayed()
            compose.onNodeWithText("Loading details for Before refresh…").assertDoesNotExist()
            release.countDown()
            assertTrue("Old details lookup did not return", returned.await(5, TimeUnit.SECONDS))
            compose.waitForIdle()
            compose.onNodeWithText("Obsolete repository details").assertDoesNotExist()
            compose.onNodeWithText("Library details").assertDoesNotExist()
        } finally {
            release.countDown()
        }
    }

    @Test
    fun libraryContentRefreshWithStableIdClearsExpandedDetails() {
        val gateway = RecordingLibraryDetailsGateway()
        var repository by mutableStateOf<LibraryScreenState>(
            LibraryScreenState.Ready(listOf(LibraryRowModel("item-1", "Fixture video", "720p"))),
        )
        compose.setContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = repository,
                detailsGatewayProvider = { gateway },
                onUpdateSettings = {},
                onQueryChanged = {},
            )
        }
        compose.onNodeWithText("Details").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Library details").assertIsDisplayed() }.isSuccess
        }
        compose.runOnIdle {
            repository = LibraryScreenState.Ready(
                listOf(LibraryRowModel("item-1", "Updated fixture video", "1080p")),
            )
        }
        compose.onNodeWithText("Updated fixture video").assertIsDisplayed()
        compose.onNodeWithText("Library details").assertDoesNotExist()
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
}
