package com.ekkus.offlineytplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.ekkus.offlineytplayer.coregateway.AppSourceAnalysisGateway
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreSourceAnalysis
import com.ekkus.offlineytplayer.coregateway.CoreSourceQualityChoice
import com.ekkus.offlineytplayer.playback.LocalAudioTrack
import com.ekkus.offlineytplayer.playback.LocalPlaybackAsset
import com.ekkus.offlineytplayer.playback.LocalSubtitleTrack
import com.ekkus.offlineytplayer.settings.AppSettingsSnapshot
import com.ekkus.offlineytplayer.settings.AppearanceSetting
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProductionComposeGoldenTest {
    @get:Rule
    val compose = createComposeRule()

    private val lightSettings = AppSettingsSnapshot(appearance = AppearanceSetting.Light)

    @Test
    fun golden_library_empty() {
        setGoldenContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(emptyList()),
                settings = lightSettings,
            )
        }
        compose.onNodeWithText("No offline videos yet").assertIsDisplayed()
        assertGolden("library_empty")
    }

    @Test
    fun golden_library_populated() {
        setGoldenContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(
                        LibraryRowModel("one", "Fixture one", "720p · 0:42", completed = false),
                        LibraryRowModel("two", "Fixture two", "1080p · 1:23", completed = false),
                    ),
                ),
                settings = lightSettings,
            )
        }
        compose.onNodeWithText("Fixture one").assertIsDisplayed()
        compose.onNodeWithText("Fixture two").assertIsDisplayed()
        assertGolden("library_populated")
    }

    @Test
    fun golden_add_invalid() {
        setGoldenContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "not-a-video-url",
                sourceAnalysisGateway = RejectingSourceAnalysisGateway(),
                settingsSnapshot = lightSettings,
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Invalid fixture URL").assertIsDisplayed() }.isSuccess
        }
        assertGolden("add_invalid")
    }

    @Test
    fun golden_add_resolved() {
        setResolvedAddContent()
        compose.onNodeWithText("Fixture source").assertIsDisplayed()
        assertGolden("add_resolved")
    }

    @Test
    fun golden_download_setup_options() {
        setResolvedAddContent()
        compose.onNodeWithText("Options").performClick()
        compose.onNodeWithText("Quality choices").assertIsDisplayed()
        assertGolden("download_setup")
    }

    @Test
    fun golden_downloads_active() {
        setGoldenContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "active",
                            title = "Fixture active download",
                            state = DownloadUiState.Active,
                            percent = 42,
                            size = "42 / 100 MB",
                            stateLabel = "Downloading",
                            speedLabel = "2.4 MB/s",
                            etaLabel = "ETA 24s",
                        ),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Fixture active download").assertIsDisplayed()
        assertGolden("downloads_active")
    }

    @Test
    fun golden_downloads_failure() {
        setGoldenContent {
            DownloadsScreen(
                state = DownloadsScreenState.Ready(
                    listOf(
                        DownloadRowModel(
                            id = "failed",
                            title = "Fixture failed download",
                            state = DownloadUiState.Failed,
                            percent = 37,
                            size = "37 / 100 MB",
                            error = "Deterministic fixture write failure",
                            stateLabel = "Failed",
                            speedLabel = "Speed unavailable",
                            etaLabel = "ETA unavailable",
                        ),
                    ),
                ),
            )
        }
        compose.onNodeWithText("Error: Deterministic fixture write failure").assertIsDisplayed()
        assertGolden("downloads_failure")
    }

    @Test
    fun golden_player() {
        setGoldenContent {
            PortraitPlayerScreen(
                asset = LocalPlaybackAsset(
                    videoPath = "/data/local/tmp/golden-video.mp4",
                    audioPath = "/data/local/tmp/golden-audio.m4a",
                    title = "Fixture player",
                    subtitleTracks = listOf(
                        LocalSubtitleTrack(
                            path = "/data/local/tmp/golden-en.vtt",
                            language = "en",
                            label = "English",
                            mimeType = "text/vtt",
                        ),
                    ),
                    audioTracks = listOf(
                        LocalAudioTrack(
                            path = "/data/local/tmp/golden-audio-en.m4a",
                            language = "en",
                            label = "Main",
                        ),
                    ),
                ),
                settings = lightSettings,
                onUpdateSettings = {},
                onBack = {},
            )
        }
        compose.onNodeWithText("Fixture player").assertIsDisplayed()
        compose.waitForIdle()
        assertGolden("player")
    }

    @Test
    fun golden_settings_hub() {
        setGoldenContent {
            OfflineYTPlayerApp(settingsSnapshot = lightSettings)
        }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Choose a settings category. Primary settings stay on dedicated fixed-layout pages.").assertIsDisplayed()
        assertGolden("settings_hub")
    }

    @Test
    fun golden_smallest_supported_portrait() {
        setGoldenContent {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("compact", "Compact fixture", "720p · 0:42", completed = false)),
                ),
                settings = lightSettings,
            )
        }
        compose.onNodeWithText("Compact fixture").assertIsDisplayed()
        assertGolden("smallest_portrait")
    }

    @Test
    fun golden_large_font_library() {
        setGoldenContent(fontScale = 1.30f) {
            LibraryScreen(
                onAdd = {},
                onPlay = {},
                state = LibraryScreenState.Ready(
                    listOf(LibraryRowModel("large-font", "Large-font fixture", "720p · 0:42", completed = false)),
                ),
                settings = lightSettings,
            )
        }
        compose.onNodeWithText("Large-font fixture").assertIsDisplayed()
        assertGolden("large_font_library")
    }

    @Test
    fun golden_large_font_settings() {
        setGoldenContent(fontScale = 1.30f) {
            OfflineYTPlayerApp(settingsSnapshot = lightSettings)
        }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("About").assertIsDisplayed()
        assertGolden("large_font_settings")
    }

    private fun setResolvedAddContent() {
        setGoldenContent {
            OfflineYTPlayerApp(
                initialSharedUrl = "https://youtu.be/dQw4w9WgXcQ",
                sourceAnalysisGateway = GoldenSourceAnalysisGateway(),
                settingsSnapshot = lightSettings,
            )
        }
        compose.onNodeWithText("Analyze").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Fixture source").assertIsDisplayed() }.isSuccess
        }
    }

    private fun setGoldenContent(
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density = deviceDensity, fontScale = fontScale),
            ) {
                OfflineYTPlayerTheme(AppearanceSetting.Light) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertGolden(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val outputDirectory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "goldens",
        ).apply { mkdirs() }
        val output = File(outputDirectory, "$name.png")
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Failed to encode golden screenshot $name"
            }
        }
        val actual = bitmapRasterSha256(bitmap)
        File(outputDirectory, "golden-raster-sha256.txt").appendText("$name=$actual\n")
        persistGoldenEvidence(output, name, actual)
        val expected = expectedHashes.getValue(name)
        assertTrue(
            "Golden '$name' changed. Review the PNG artifact before updating pinned raster SHA-256 variants. actual=$actual expected=${expected.sorted()}",
            actual in expected,
        )
    }

    private fun persistGoldenEvidence(output: File, name: String, actual: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val evidenceDir = "/sdcard/Download/offline-yt-player-goldens"
        val command = "mkdir -p $evidenceDir && cp '${output.absolutePath}' '$evidenceDir/$name.png' && printf '%s\\n' '$name=$actual' >> '$evidenceDir/golden-raster-sha256.txt'"
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input -> input.readBytes() }
        }
    }

    private fun bitmapRasterSha256(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun updateInt(value: Int) {
            digest.update(((value ushr 24) and 0xff).toByte())
            digest.update(((value ushr 16) and 0xff).toByte())
            digest.update(((value ushr 8) and 0xff).toByte())
            digest.update((value and 0xff).toByte())
        }
        updateInt(bitmap.width)
        updateInt(bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pixels.forEach(::updateInt)
        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    companion object {
        private val expectedHashes = mapOf(
            "library_empty" to setOf(
                "1ab01c25c1fbb2d165311e895b83fcd82648c79eac6939a6770d9fe826a772f2",
                "f6a008040ff2bc3c3dd1aa170aec1e2f09c597e55dc7bf936dcfa7c5b0ad0443",
            ),
            "library_populated" to setOf(
                "69a0c4a62f147af7b3fbccfc09fb3e9952a6491ad6e0ccb263d1a92e1f097c32",
                "6be56fdbea5f3a875ec625514c059a59824777488758b4086568c2603fdfe628",
            ),
            "add_invalid" to setOf(
                "a0de221ecc84f92c725c6077d121ec3e9b11d07351dc20cf67eb5db62bf28a6e",
                "64feb00fe7a58a43e49b1dfc66055894145b9ce7d3e15c40ab2f23069e5c2d14",
            ),
            "add_resolved" to setOf(
                "0a57f16ceed3ac30f5f2ef26a2bd56f92991018788dc64a64bdb0ca654174d8c",
                "6a2ea0e3e5bddbe0a32a33b671c112fe5e548c0c3dfeaa885b3c8d6a2d34722a",
            ),
            "download_setup" to setOf(
                "400b8f89775e011c0e2e285aca7bab40bfe0c099546a2e42c71ff3928adb1958",
                "afb9a1a883beadc48b61658c6d43f7cc96819db7bb0705453d4a7bb5f5651476",
            ),
            "downloads_active" to setOf("03bafe07f7a171f05b6fbba259f34a3900575fede548c58484243327b91a4945"),
            "downloads_failure" to setOf("49586fc31be38ef9c955e7e93782196c728be6baad292f7d7b576695a2c5c1ac"),
            "player" to setOf(
                "bf3575836add842e2d9dae445c77c1194a6247ff26f9adac3a7851c3bd353f77",
                "9d29c633509966b60823cde060d681cb3dbd7b9849607c5576bb7879797b0ed2",
            ),
            "settings_hub" to setOf("907224d90bc0a87418bed06661427bc4dfc6aa9871a64348fc03ba238fac0052"),
            "smallest_portrait" to setOf("54e27dfa0c00e4ee672d13ac7d80193512406be63a0a015ac1a44fce66fc8498"),
            "large_font_library" to setOf("307936d82d0b4bd812638249cb888112437355f73852c697a6c1cba2487a9838"),
            "large_font_settings" to setOf("4c6185c13f3576d592a4dac8934b750c360cfa9c6e0e1b4181cac96423115611"),
        )
    }
}

private class GoldenSourceAnalysisGateway(
    private val analysis: CoreSourceAnalysis = CoreSourceAnalysis(
        sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
        title = "Fixture source",
        durationMs = 42_000,
        thumbnailUrl = "https://example.test/thumb.jpg",
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

private class RejectingSourceAnalysisGateway : AppSourceAnalysisGateway {
    override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> =
        CoreGatewayResult(
            value = null,
            error = CoreGatewayError(
                kind = "invalid_fixture",
                message = "Invalid fixture URL",
                retryable = false,
            ),
        )

    override fun close() = Unit
}
