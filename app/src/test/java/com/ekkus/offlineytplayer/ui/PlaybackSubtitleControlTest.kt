package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSubtitleControlTest {
    @Test
    fun subtitleSelectionCyclesFromOffThroughTracksAndBackOff() {
        assertEquals(0, nextSubtitleIndex(-1, 2))
        assertEquals(1, nextSubtitleIndex(0, 2))
        assertEquals(-1, nextSubtitleIndex(1, 2))
        assertEquals(-1, nextSubtitleIndex(-1, 0))
    }

    @Test
    fun subtitleLabelReflectsActualSelectionAndDisabledState() {
        val labels = listOf("English", "Español")

        assertEquals("Subtitles: Off", subtitleControlLabel(labels, -1))
        assertEquals("Subtitles: English", subtitleControlLabel(labels, 0))
        assertEquals("Subtitles: Español", subtitleControlLabel(labels, 1))
        assertEquals("Subtitles", subtitleControlLabel(emptyList(), -1))
    }
}
