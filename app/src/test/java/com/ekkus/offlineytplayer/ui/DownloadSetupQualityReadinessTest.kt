package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSetupQualityReadinessTest {
    @Test
    fun changingQualityRecomputesReadinessAndExecutableChoiceInBothDirections() {
        val initial = DownloadSetupState(
            sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
            title = "Fixture",
            durationLabel = "0:42",
            qualityLabel = "Preferred",
            estimatedSizeLabel = "Size unavailable",
            readyForDownload = false,
            qualityOptions = listOf("Preferred", "Playable"),
            qualityChoiceIdsByLabel = mapOf("Preferred" to "", "Playable" to "format-720"),
            qualityEstimatedBytesByLabel = mapOf("Playable" to 1024L),
            selectedQualityChoiceId = "",
        )
        assertFalse(initial.readyForDownload)

        val playable = setupWithQuality(initial, "Playable")
        assertEquals("Playable", playable.qualityLabel)
        assertEquals("format-720", playable.selectedQualityChoiceId)
        assertEquals("1024 bytes", playable.estimatedSizeLabel)
        assertTrue(playable.readyForDownload)

        val unavailableAgain = setupWithQuality(playable, "Preferred")
        assertEquals("Preferred", unavailableAgain.qualityLabel)
        assertEquals("", unavailableAgain.selectedQualityChoiceId)
        assertFalse(unavailableAgain.readyForDownload)

        val unknown = setupWithQuality(playable, "Missing")
        assertEquals(null, unknown.selectedQualityChoiceId)
        assertFalse(unknown.readyForDownload)
    }
}
