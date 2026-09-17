package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataPresentationPolicyTest {
    @Test
    fun primaryMetadataIsCompactAndBounded() {
        val compact = MetadataPresentationPolicy.compact(
            title = "  Example title  ",
            duration = " 12:34 ",
            quality = " 1080p ",
            source = " YouTube ",
        )

        assertEquals("Example title", compact.title)
        assertEquals("12:34", compact.duration)
        assertEquals("1080p", compact.quality)
        assertEquals("YouTube", compact.source)
        assertEquals(2, MetadataPresentationPolicy.PrimaryTitleMaxLines)
        assertEquals(3, MetadataPresentationPolicy.PrimaryMetadataFieldCount)
        assertFalse(MetadataPresentationPolicy.PrimaryScreenUsesUnboundedMetadata)
    }

    @Test
    fun longDetailsMoveToDedicatedDeterministicRegion() {
        val details = MetadataPresentationPolicy.details(
            mapOf(
                "Codec" to "H.264",
                "Description" to "Long source description",
                "" to "ignored",
                "Empty" to "  ",
            ),
        )

        assertTrue(MetadataPresentationPolicy.LongDetailsUseDedicatedRegion)
        assertEquals(
            listOf(
                MetadataDetailRow("Codec", "H.264"),
                MetadataDetailRow("Description", "Long source description"),
            ),
            details,
        )
    }
}
