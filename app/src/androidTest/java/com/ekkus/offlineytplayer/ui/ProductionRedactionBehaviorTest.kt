package com.ekkus.offlineytplayer.ui

import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.CoreLibraryDetailAsset
import com.ekkus.offlineytplayer.coregateway.CoreLibraryDetails
import com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionRedactionBehaviorTest {
    @get:Rule
    val compose = createComposeRule()


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
}
