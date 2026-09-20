package com.ekkus.offlineytplayer.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddScreenPolicyTest {
    @Test fun primaryAddUiIsCompleteAndNonScrolling() { val policy = AddScreenPolicy(); assertTrue(policy.urlFieldVisible); assertTrue(policy.pasteActionVisible); assertTrue(policy.analyzeActionVisible); assertTrue(policy.supportedSourceHintVisible); assertFalse(policy.primaryScreenScrollable); assertTrue(policy.primaryControlsFit()) }
    @Test fun analyzeRequiresHttpUrlCandidate() { val policy = AddScreenPolicy(); assertTrue(policy.canAnalyze("https://www.youtube.com/watch?v=fixture")); assertTrue(policy.canAnalyze(" http://example.test/video ")); assertFalse(policy.canAnalyze("not a url")); assertFalse(policy.canAnalyze("file:///tmp/video.mp4")) }

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
}
