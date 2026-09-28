package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddWorkflowPolicyTest {
    @Test
    fun boundedClipboardTextHandlesMissingAndBlankTextGracefully() {
        assertNull(AddWorkflowPolicy.boundedClipboardText(null).acceptedText)
        assertEquals(
            AddWorkflowPolicy.MissingClipboardMessage,
            AddWorkflowPolicy.boundedClipboardText("   ").statusMessage,
        )
    }

    @Test
    fun boundedClipboardTextLimitsAcceptedInputBeforeAnalyze() {
        val accepted = AddWorkflowPolicy.boundedClipboardText("x".repeat(AddWorkflowPolicy.MaxClipboardChars + 20))
        assertEquals(AddWorkflowPolicy.MaxClipboardChars, accepted.acceptedText!!.length)
        assertEquals(AddWorkflowPolicy.PastedClipboardMessage, accepted.statusMessage)
    }

    @Test
    fun qualityLabelsUseResolvedOptionsBeforeFallback() {
        assertEquals(
            listOf("720p", "Audio only"),
            AddWorkflowPolicy.qualityLabels(listOf("720p", "720p", " ", "Audio only"), "Best"),
        )
        assertEquals(listOf("Best"), AddWorkflowPolicy.qualityLabels(emptyList(), "Best"))
    }

    @Test
    fun preferredQualityHonorsConfiguredValueOnlyWhenAvailable() {
        assertEquals(
            "Audio only",
            AddWorkflowPolicy.preferredQualityLabel(listOf("720p", "Audio only"), "audio ONLY", "720p"),
        )
        assertEquals(
            "720p",
            AddWorkflowPolicy.preferredQualityLabel(listOf("720p"), "Audio only", "720p"),
        )
    }

    @Test
    fun optionSummaryDeduplicatesAndFallsBack() {
        assertEquals("English · Spanish", AddWorkflowPolicy.optionSummary(listOf("English", "English", " Spanish "), "None"))
        assertEquals("None", AddWorkflowPolicy.optionSummary(listOf(" "), "None"))
    }

    @Test
    fun downloadSettingsSummaryReflectsRuntimeSettings() {
        assertTrue(AddWorkflowPolicy.downloadSettingsSummary(true, 3).contains("Wi-Fi only"))
        assertEquals("Any network · 2 concurrent", AddWorkflowPolicy.downloadSettingsSummary(false, 2))
    }
}
