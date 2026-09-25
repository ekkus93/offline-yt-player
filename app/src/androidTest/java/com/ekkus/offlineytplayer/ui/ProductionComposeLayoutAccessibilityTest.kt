package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.AppearanceSetting
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductionComposeLayoutAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val lightSettings = AppSettingsSnapshot(appearance = AppearanceSetting.Light)

    @Test
    fun compact_app_shell_keeps_every_primary_destination_reachable() {
        setQualificationContent {
            OfflineYTPlayerApp(
                libraryState = LibraryScreenState.Ready(emptyList()),
                settingsSnapshot = lightSettings,
            )
        }

        listOf(
            "Open offline library",
            "Open downloads",
            "Add a video",
            "Open settings",
        ).forEach { description ->
            assertInsideRoot(compose.onNodeWithContentDescription(description))
        }
    }

    @Test
    fun compact_library_keeps_fixed_primary_actions_visible_without_horizontal_scroll() {
        setQualificationContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(emptyList()),
                settings = lightSettings,
            )
        }

        assertInsideRoot(compose.onNodeWithText("Search library"))
        assertInsideRoot(compose.onNodeWithText("Grid"))
        assertInsideRoot(compose.onNodeWithText("Add video"))
    }

    @Test
    fun compact_add_and_resolved_setup_keep_primary_actions_reachable() {
        setQualificationContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = LayoutSourceAnalysisGateway(),
                settingsSnapshot = lightSettings,
            )
        }

        assertInsideRoot(compose.onNodeWithText("Paste"))
        assertInsideRoot(compose.onNodeWithText("Analyze"))
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Fixture layout source").assertIsDisplayed() }.isSuccess
        }
        assertInsideRoot(compose.onNodeWithText("Options"))
        assertInsideRoot(compose.onNodeWithText("Download"))
    }

    @Test
    fun compact_downloads_keep_filters_and_row_actions_reachable() {
        setQualificationContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "active",
                            title = "Compact active download",
                            state = DownloadUiState.Active,
                            percent = 25,
                            size = "25 / 100 MB",
                        ),
                    ),
                ),
            )
        }

        assertInsideRoot(compose.onNodeWithText("[All]"))
        assertInsideRoot(compose.onNodeWithText("Paused"))
        assertInsideRoot(compose.onNodeWithText("Completed"))
        assertInsideRoot(compose.onNodeWithText("Pause"))
        assertInsideRoot(compose.onNodeWithText("Cancel"))
        assertInsideRoot(compose.onNodeWithText("Details"))
    }

    @Test
    fun compact_settings_hub_keeps_categories_reachable_in_logical_order() {
        setQualificationContent {
            OfflineYTPlayerApp(settingsSnapshot = lightSettings)
        }
        compose.onNodeWithText("Settings").performClick()

        val playback = compose.onNodeWithText("Playback")
        val storage = compose.onNodeWithText("Storage")
        val appearance = compose.onNodeWithText("Appearance")
        val about = compose.onNodeWithText("About")
        listOf(playback, storage, appearance, about).forEach(::assertInsideRoot)

        val tops = listOf(playback, storage, appearance, about).map {
            it.fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("Settings traversal order must follow visual top-to-bottom order: $tops", tops.zipWithNext().all { it.first < it.second })
    }

    @Test
    fun compact_player_keeps_transport_and_secondary_controls_reachable() {
        setQualificationContent {
            PortraitPlayerScreen(
                asset = LocalPlaybackAsset(
                    videoPath = "/data/local/tmp/layout-video.mp4",
                    title = "Compact player",
                ),
                settings = lightSettings,
                onUpdateSettings = {},
                onBack = {},
            )
        }

        listOf("Back", "-10s", "Play/Pause", "+10s", "Speed 1.0×", "Subtitles", "Audio").forEach { label ->
            assertInsideRoot(compose.onNodeWithText(label))
        }
    }

    @Test
    fun large_text_keeps_representative_primary_actions_visible() {
        setQualificationContent(fontScale = 1.30f) {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(emptyList()),
                settings = lightSettings,
            )
        }
        assertInsideRoot(compose.onNodeWithText("Grid"))
        assertInsideRoot(compose.onNodeWithText("Add video"))

        setQualificationContent(fontScale = 1.30f) {
            OfflineYTPlayerApp(settingsSnapshot = lightSettings)
        }
        compose.onNodeWithText("Settings").performClick()
        listOf("Playback", "Storage", "Appearance", "About").forEach { label ->
            assertInsideRoot(compose.onNodeWithText(label))
        }
    }

    @Test
    fun navigation_and_actionable_controls_expose_talkback_semantics_and_minimum_touch_targets() {
        setQualificationContent {
            OfflineYTPlayerApp(
                libraryState = LibraryScreenState.Ready(emptyList()),
                settingsSnapshot = lightSettings,
            )
        }

        listOf(
            "Open offline library",
            "Open downloads",
            "Add a video",
            "Open settings",
        ).forEach { description ->
            val node = compose.onNodeWithContentDescription(description)
            node.assertIsDisplayed()
            assertMinimumTouchTarget(node)
        }
        assertMinimumTouchTarget(compose.onNodeWithText("Add video"))
        assertMinimumTouchTarget(compose.onNodeWithText("Grid"))
    }

    @Test
    fun failed_download_state_is_expressed_textually_not_by_color_alone() {
        setQualificationContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "failed",
                            title = "Failed fixture",
                            state = DownloadUiState.Failed,
                            percent = 37,
                            size = "37 / 100 MB",
                            error = "Fixture failure",
                            stateLabel = "Failed",
                            speedLabel = "Speed unavailable",
                            etaLabel = "ETA unavailable",
                        ),
                    ),
                ),
            )
        }

        compose.onNodeWithText("Failed · 37% · 37 / 100 MB · Speed unavailable · ETA unavailable").assertIsDisplayed()
        compose.onNodeWithText("Error: Fixture failure").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun long_library_uses_designed_vertical_collection_scrolling() {
        setQualificationContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    (0..19).map { index ->
                        LibraryRowModel(
                            id = "fixture-$index",
                            title = "Fixture $index",
                            detail = "720p · 0:42",
                            completed = false,
                        )
                    },
                ),
                settings = lightSettings,
            )
        }

        compose.onNodeWithText("Fixture 19").performScrollTo().assertIsDisplayed()
        assertInsideRoot(compose.onNodeWithText("Fixture 19"))
    }

    private fun setQualificationContent(
        widthDp: Int = 360,
        heightDp: Int = 640,
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                OfflineYTPlayerTheme(AppearanceSetting.Light) {
                    Surface(modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInsideRoot(interaction: SemanticsNodeInteraction) {
        interaction.assertIsDisplayed()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bounds = interaction.fetchSemanticsNode().boundsInRoot
        val epsilon = 0.5f
        assertTrue("Control starts outside root: root=$root bounds=$bounds", bounds.left >= root.left - epsilon)
        assertTrue("Control ends outside root: root=$root bounds=$bounds", bounds.right <= root.right + epsilon)
        assertTrue("Control starts above root: root=$root bounds=$bounds", bounds.top >= root.top - epsilon)
        assertTrue("Control ends below root: root=$root bounds=$bounds", bounds.bottom <= root.bottom + epsilon)
    }

    private fun assertMinimumTouchTarget(interaction: SemanticsNodeInteraction) {
        val bounds = interaction.fetchSemanticsNode().boundsInRoot
        assertTrue("Touch target must be at least 48dp high: $bounds", bounds.height >= 48f)
        assertTrue("Touch target must be at least 48dp wide: $bounds", bounds.width >= 48f)
    }
}

private class LayoutSourceAnalysisGateway(
    private val analysis: CoreSourceAnalysis = CoreSourceAnalysis(
        sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
        title = "Fixture layout source",
        durationMs = 42_000,
        thumbnailUrl = "https://example.test/layout-thumb.jpg",
        qualityLabel = "720p",
        estimatedBytes = 12L * 1024L * 1024L,
        qualityOptions = listOf(
            CoreSourceQualityChoice("720p", 12L * 1024L * 1024L),
            CoreSourceQualityChoice("Audio only", 2L * 1024L * 1024L),
        ),
    ),
) : AppSourceAnalysisGateway {
    override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> =
        CoreGatewayResult(value = analysis, error = null)

    override fun close() = Unit
}
