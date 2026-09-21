package com.ekkus.offlineytplayer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentPolicyTest {
    @Test
    fun manifestRegistersPlainTextSendIntent() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.intent.action.SEND"))
        assertTrue(manifest.contains("android.intent.category.DEFAULT"))
        assertTrue(manifest.contains("android:mimeType=\"text/plain\""))
    }

    @Test
    fun activityParsesShareBeforeRenderingApp() {
        val activity = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        assertTrue(activity.contains("ShareInput.parse("))
        assertTrue(activity.contains("intent?.getStringExtra(Intent.EXTRA_TEXT)"))
        assertTrue(activity.contains("initialSharedUrl = sharedUrl"))
    }

    @Test
    fun activityOwnsProductionGatewaysAndLoadsRepositoryStateOffMainThread() {
        val activity = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        assertTrue(activity.contains("GeneratedUniffiCoreGateway.open(databasePath)"))
        assertTrue(activity.contains("GeneratedUniffiDownloadControlGateway.open(databasePath)"))
        assertTrue(activity.contains("GeneratedUniffiLibraryPlaybackGateway.open(databasePath)"))
        assertTrue(activity.contains("GeneratedUniffiSourceAnalysisGateway.open()"))
        assertTrue(activity.contains("bootstrapExecutor.execute"))
        assertTrue(activity.contains("listLibrary().toLibraryScreenState(libraryRoot, initialPlaybackAssets)"))
        assertTrue(activity.contains("listDownloadQueue().toDownloadsScreenState()"))
        assertTrue(activity.contains("downloadControlGateway = downloadControlGateway"))
        assertTrue(activity.contains("sourceAnalysisGateway = sourceAnalysisGateway"))
    }
}
