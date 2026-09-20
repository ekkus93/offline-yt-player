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
        assertTrue(activity.contains("bootstrapExecutor.execute"))
        assertTrue(activity.contains("gateway.listLibrary()"))
        assertTrue(activity.contains("gateway.listDownloadQueue()"))
        assertTrue(activity.contains("downloadControlGateway = downloadControlGateway"))
    }
}
