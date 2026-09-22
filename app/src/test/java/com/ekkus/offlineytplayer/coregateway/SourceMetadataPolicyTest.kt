package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMetadataPolicyTest {
    @Test fun boundsAndTrimsSourceMetadata() {
        assertEquals("Title", SourceMetadataPolicy.title("  Title  "))
        assertEquals("Channel", SourceMetadataPolicy.channel(" Channel "))
        assertEquals("Description", SourceMetadataPolicy.description(" Description "))
        assertEquals("720p", SourceMetadataPolicy.qualityLabel(" 720p "))
        assertEquals(SourceMetadataPolicy.MaxTitleChars, SourceMetadataPolicy.title("x".repeat(2000)).length)
        assertEquals(SourceMetadataPolicy.MaxChannelChars, SourceMetadataPolicy.channel("c".repeat(2000)).length)
        assertEquals(SourceMetadataPolicy.MaxDescriptionChars, SourceMetadataPolicy.description("d".repeat(8000)).length)
        assertEquals(SourceMetadataPolicy.MaxQualityLabelChars, SourceMetadataPolicy.qualityLabel("q".repeat(1000)).length)
        assertEquals(SourceMetadataPolicy.MaxDiagnosticChars, SourceMetadataPolicy.diagnostic("e".repeat(2000)).length)
    }

    @Test fun malformedMetadataIsSanitizedForPresentation() {
        val bounded = SourceMetadataPolicy.title("\u0000Bad\nTitle\r\u0007" + "z".repeat(4000))
        assertTrue(bounded.length <= SourceMetadataPolicy.MaxTitleChars)
        assertFalse(bounded.any(Char::isISOControl))
        assertTrue(bounded.startsWith("Bad Title"))
        assertFalse(SourceMetadataPolicy.description("bad\u0000\ntext").any(Char::isISOControl))
        assertEquals("Untitled video", SourceMetadataPolicy.title("\u0000\u0007"))
        assertEquals("Unknown channel", SourceMetadataPolicy.channel("\u0000"))
        assertEquals("No description", SourceMetadataPolicy.description("\u0007"))
        assertEquals("Unknown quality", SourceMetadataPolicy.qualityLabel("\n\r\t"))
        assertEquals("Unknown error", SourceMetadataPolicy.diagnostic("\u0000"))
    }

    @Test fun diagnosticRedactsSyntheticSignedUrls() {
        val marker = "RMD1302_MARKER"
        val input = "GET https://user:$marker@cdn.example/video?sig=$marker#frag-$marker"
        val output = SourceMetadataPolicy.diagnostic(input)
        assertFalse(output.contains(marker))
        assertFalse(output.contains("sig="))
        assertFalse(output.contains("frag-"))
        assertTrue(output.contains("cdn.example/video"))
    }

    @Test fun ordinaryDiagnosticTextIsPreserved() {
        assertEquals("download failed: connection reset", SourceMetadataPolicy.diagnostic("download failed: connection reset"))
    }
}
