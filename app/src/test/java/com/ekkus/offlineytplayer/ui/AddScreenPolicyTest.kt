package com.ekkus.offlineytplayer.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddScreenPolicyTest {
    @Test fun primaryAddUiIsCompleteAndNonScrolling() { val policy = AddScreenPolicy(); assertTrue(policy.urlFieldVisible); assertTrue(policy.pasteActionVisible); assertTrue(policy.analyzeActionVisible); assertTrue(policy.supportedSourceHintVisible); assertFalse(policy.primaryScreenScrollable); assertTrue(policy.primaryControlsFit()) }
    @Test fun analyzeRequiresSupportedVideoUrl() { val policy = AddScreenPolicy(); assertTrue(policy.canAnalyze("https://www.youtube.com/watch?v=dQw4w9WgXcQ")); assertTrue(policy.canAnalyze(" https://youtu.be/dQw4w9WgXcQ ")); assertFalse(policy.canAnalyze("http://example.test/video")); assertFalse(policy.canAnalyze("not a url")); assertFalse(policy.canAnalyze("file:///tmp/video.mp4")) }

    @Test fun productionAddAnalysisUsesInjectedSourceGatewayNotPreviewRoute() {
        val source = Files.readString(Paths.get("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt"))
        assertTrue(source.contains("sourceGateway.analyze") || source.contains("gateway.analyze"))
        assertFalse(source.contains("DownloadSetupRoute.previewFor(url)"))
        assertTrue(source.contains("withContext(Dispatchers.IO)"))
    }

    @Test fun sourceAnalysisCancelsSupersededRequests() {
        val source = Files.readString(Paths.get("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt"))
        assertTrue(source.contains("analysisJob?.cancel()"))
        assertTrue(source.contains("activeAnalysisUrl"))
        assertTrue(source.contains("DisposableEffect(Unit)"))
    }

    @Test fun downloadSetupSchedulesThroughControlGateway() {
        val source = Files.readString(Paths.get("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt"))
        assertTrue(source.contains("downloadControlGateway"))
        assertTrue(source.contains("gateway.enqueue(jobId)"))
        assertTrue(source.contains("Download scheduled."))
        assertFalse(source.contains("Download scheduling requires the durable production worker wiring"))
    }

    @Test fun advancedOptionsReflectSourceDerivedSetupState() {
        val source = Files.readString(Paths.get("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt"))
        val model = Files.readString(Paths.get("src/main/java/com/ekkus/offlineytplayer/ui/DownloadSetupModels.kt"))
        assertTrue(model.contains("qualityOptions"))
        assertTrue(model.contains("subtitleOptions"))
        assertTrue(model.contains("audioOptions"))
        assertTrue(model.contains("containerOptions"))
        assertTrue(source.contains("analysis.qualityOptions"))
        assertTrue(source.contains("AdvancedDownloadOptions(padding, activeSetup)"))
        assertTrue(source.contains("None reported by source"))
        assertFalse(source.contains("SettingValue(\"Subtitle language\", \"Preferred\")"))
    }
}
