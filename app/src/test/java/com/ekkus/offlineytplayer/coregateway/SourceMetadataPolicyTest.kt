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

    @Test fun providerMetadataPresentationRedactsSyntheticCredentials() {
        val marker = "RMD1802_PRIVATE_METADATA_VALUE"
        val signedUrl = "https://cdn.example/video?sig=$marker"
        val displayed = listOf(
            SourceMetadataPolicy.title("Title $signedUrl"),
            SourceMetadataPolicy.channel("Channel $signedUrl"),
            SourceMetadataPolicy.description("Description Authorization: Bearer $marker"),
            SourceMetadataPolicy.qualityLabel("720p $signedUrl"),
        )
        displayed.forEach { assertFalse(it.contains(marker)) }
        assertTrue(displayed[0].contains("video?REDACTED"))
        assertTrue(displayed[1].contains("video?REDACTED"))
        assertTrue(displayed[2].contains("Authorization: [REDACTED]"))
        assertTrue(displayed[3].contains("video?REDACTED"))
        assertTrue(displayed[0].length <= SourceMetadataPolicy.MaxTitleChars)
        assertTrue(displayed[1].length <= SourceMetadataPolicy.MaxChannelChars)
        assertTrue(displayed[2].length <= SourceMetadataPolicy.MaxDescriptionChars)
        assertTrue(displayed[3].length <= SourceMetadataPolicy.MaxQualityLabelChars)
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

    @Test fun diagnosticRedactsCredentialHeadersEvenWhenPrefixedOrIndented() {
        val marker = "SECRET_HEADER_1802"
        val input = listOf(
            "2026-10-12 ERROR Authorization: Bearer $marker",
            "  Cookie: session=$marker",
            "\tProxy-Authorization: Basic $marker",
            "request failed: X-Api-Key: $marker",
            "Set-Cookie: session=$marker",
        ).joinToString("\n")
        val output = SourceMetadataPolicy.diagnostic(input)
        assertFalse(output.contains(marker))
        assertTrue(output.contains("Authorization: [REDACTED]"))
        assertTrue(output.contains("Cookie: [REDACTED]"))
        assertTrue(output.contains("X-Api-Key: [REDACTED]"))
        assertTrue(output.length <= SourceMetadataPolicy.MaxDiagnosticChars)
    }

    @Test fun diagnosticRedactsSignedUrlBeforeSensitiveHeaderOnSameLine() {
        val marker = "SECRET_MIXED_1802"
        val output = SourceMetadataPolicy.diagnostic(
            "GET https://cdn.example/video?sig=$marker#fragment-$marker Authorization: Bearer $marker",
        )
        assertFalse(output.contains(marker))
        assertTrue(output.contains("cdn.example/video"))
        assertTrue(output.contains("Authorization: [REDACTED]"))
    }

    @Test fun nestedUrlCredentialsInPathNeverReachMetadataOrDiagnostics() {
        val marker = "NESTED_PRIVATE_RMD1802"
        val nested = "https://outer.example/archive/https://user:$marker@inner.example/video"
        val encoded = "https://outer.example/archive/https%3A%2F%2Fuser%3A$marker%40inner.example"
        val displayed = listOf(
            SourceMetadataPolicy.title("Title $nested"),
            SourceMetadataPolicy.channel("Channel $encoded"),
            SourceMetadataPolicy.description("Description $nested"),
            SourceMetadataPolicy.qualityLabel("720p $encoded"),
            SourceMetadataPolicy.diagnostic("GET $nested"),
            SourceMetadataPolicy.diagnostic("GET $encoded"),
        )
        displayed.forEach { value ->
            assertFalse(value.contains(marker))
            assertTrue(value.contains("[REDACTED_URL]"))
        }
        assertTrue(SourceMetadataPolicy.diagnostic("GET https://cdn.example/video/segment").contains("cdn.example/video/segment"))
    }

    @Test fun protocolRelativeAndEncodedUrlsNeverExposeCredentials() {
        val marker = "RELATIVE_URL_SECRET_1802"
        val protocolRelative = "//user:$marker@cdn.example/media?sig=$marker"
        val encoded = "https%3A%2F%2Fcdn.example%2Fmedia%3Fsig%3D$marker"
        val displayed = listOf(
            SourceMetadataPolicy.title("Title $protocolRelative"),
            SourceMetadataPolicy.channel("Channel $encoded"),
            SourceMetadataPolicy.description("Description $protocolRelative"),
            SourceMetadataPolicy.qualityLabel("1080p $encoded"),
            SourceMetadataPolicy.diagnostic("GET $protocolRelative"),
            SourceMetadataPolicy.diagnostic("GET $encoded"),
        )
        displayed.forEach { value ->
            assertFalse(value.contains(marker))
            assertTrue(value.contains("REDACTED"))
        }
        // Non-sensitive protocol-relative media paths remain legible.
        assertTrue(
            SourceMetadataPolicy.diagnostic("GET //cdn.example/media/segment")
                .contains("//cdn.example/media/segment"),
        )
    }

    @Test fun ordinaryDiagnosticTextIsPreserved() {
        assertEquals("download failed: connection reset", SourceMetadataPolicy.diagnostic("download failed: connection reset"))
    }
}
