package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.SourceMetadataPolicy
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun malformedAndVeryLongMetadataIsBoundedDeterministically() {
        val compact = MetadataPresentationPolicy.compact(
            title = "x".repeat(SourceMetadataPolicy.MaxTitleChars + 100),
            duration = null,
            quality = "q".repeat(SourceMetadataPolicy.MaxQualityLabelChars + 100),
            source = "s".repeat(SourceMetadataPolicy.MaxChannelChars + 100),
        )
        assertEquals(SourceMetadataPolicy.MaxTitleChars, compact.title.length)
        assertEquals(SourceMetadataPolicy.MaxQualityLabelChars, compact.quality?.length)
        assertEquals(SourceMetadataPolicy.MaxChannelChars, compact.source?.length)

        val details = MetadataPresentationPolicy.details(
            mapOf(
                "L".repeat(100) to "D".repeat(SourceMetadataPolicy.MaxDescriptionChars + 100),
                "" to "ignored",
                "Empty" to "  ",
            ),
        )
        assertEquals(1, details.size)
        assertEquals(64, details.single().label.length)
        assertEquals(SourceMetadataPolicy.MaxDescriptionChars, details.single().value.length)
    }

    @Test
    fun longDetailsMoveToDedicatedDeterministicRegion() {
        val details = MetadataPresentationPolicy.details(
            mapOf("Codec" to "H.264", "Description" to "Long source description"),
        )
        assertEquals(
            listOf(
                MetadataDetailRow("Codec", "H.264"),
                MetadataDetailRow("Description", "Long source description"),
            ),
            details,
        )
    }
}
