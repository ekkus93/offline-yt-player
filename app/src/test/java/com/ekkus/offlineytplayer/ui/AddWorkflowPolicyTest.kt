package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.SourceMetadataPolicy
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
    fun clipboardCharSequenceIsReadOnlyUpToTheLimit() {
        val source = object : CharSequence {
            override val length: Int = 1_000_000
            override fun get(index: Int): Char {
                check(index < AddWorkflowPolicy.MaxClipboardChars) { "read exceeded bound" }
                return 'x'
            }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
                error("unbounded clipboard subsequence must not be requested")
            override fun toString(): String =
                error("whole clipboard must not be materialized")
        }
        val accepted = AddWorkflowPolicy.boundedClipboardText(source)
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
    fun displayFormattingRedactsSignedUrlsAndBoundsUntrustedLabelsWithoutChangingMapping() {
        val privateValue = "private-value-marker"
        val label = "720p https://example.test/stream?token=$privateValue"
        assertEquals(
            "720p https://example.test/stream?REDACTED",
            AddWorkflowPolicy.displayLabel(label),
        )
        assertTrue(privateValue !in AddWorkflowPolicy.displaySummary(listOf(label), "None"))
        assertEquals(
            SourceMetadataPolicy.MaxQualityLabelChars,
            AddWorkflowPolicy.displayLabel("X".repeat(900)).length,
        )
        val summary = AddWorkflowPolicy.displaySummary((1..50).map { "Choice $it" }, "None")
        assertTrue(summary.contains("42 more"))
        assertTrue(summary.length <= SourceMetadataPolicy.MaxDiagnosticChars)
    }

    @Test
    fun downloadSettingsSummaryReflectsRuntimeSettings() {
        assertTrue(AddWorkflowPolicy.downloadSettingsSummary(true, 3).contains("Wi-Fi only"))
        assertEquals("Any network · 2 concurrent", AddWorkflowPolicy.downloadSettingsSummary(false, 2))
    }
}
