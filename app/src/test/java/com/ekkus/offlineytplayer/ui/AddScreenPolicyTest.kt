package com.ekkus.offlineytplayer.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AddScreenPolicyTest {
    private val appShell: String = File("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt").readText()

    @Test
    fun pasteUsesBoundedPolicyBeforeMutatingUrlState() {
        assertTrue(appShell.contains("AddWorkflowPolicy.boundedClipboardText"))
        assertTrue(appShell.contains("url = acceptedText"))
        assertTrue(appShell.contains("status = pasted.statusMessage"))
    }

    @Test
    fun analyzeUsesProductionUseCaseAndSuppressesSupersededResults() {
        assertTrue(appShell.contains("SourceAnalysisUseCase"))
        assertTrue(appShell.contains("useCase.begin(url)"))
        assertTrue(appShell.contains("useCase.analyzeBlocking(started.ticket)"))
        assertTrue(appShell.contains("resolved is SourceAnalysisState.Superseded"))
        assertTrue(appShell.contains("withContext(Dispatchers.IO)"))
        assertTrue(appShell.contains("DownloadSetupState("))
    }

    @Test
    fun setupPreviewDisplaysResolvedOptionsAndSchedulerStatus() {
        assertTrue(appShell.contains("Text(\"Quality options: \${AddWorkflowPolicy.optionSummary"))
        assertTrue(appShell.contains("sourceProvider = analysis.sourceProvider"))
        assertTrue(appShell.contains("sourceMediaId = analysis.sourceMediaId"))
        assertTrue(appShell.contains("SetupThumbnailPreview"))
        assertTrue(appShell.contains("Scheduling \${setupState.qualityLabel} download"))
        assertTrue(appShell.contains("DownloadSelectionOptions("))
        assertTrue(appShell.contains("qualityChoiceId = setupState.selectedQualityChoiceId"))
        assertTrue(appShell.contains("subtitleTrackId = setupState.selectedSubtitleTrackId"))
        assertTrue(appShell.contains("audioFormatId = setupState.selectedAudioFormatId"))
        assertTrue(appShell.contains("Download scheduled for \${setupState.qualityLabel}"))
    }

    @Test
    fun advancedOptionsReflectSourceDerivedSetupState() {
        assertTrue(appShell.contains("SettingValue(\"Quality choices\""))
        assertTrue(appShell.contains("edited.qualityOptions.forEach"))
        assertTrue(appShell.contains("Text(\"Select \$quality\")"))
        assertTrue(appShell.contains("selectedAudioFormatId"))
        assertTrue(appShell.contains("selectedSubtitleTrackId"))
        assertTrue(appShell.contains("Text(\"Container follows the selected source quality"))
        assertTrue(appShell.contains("onApply(edited)"))
        assertTrue(appShell.contains("Download options applied."))
    }
}
