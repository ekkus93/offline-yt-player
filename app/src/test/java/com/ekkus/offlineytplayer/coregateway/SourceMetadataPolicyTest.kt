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

    @Test fun foldedSensitiveHeaderLinesNeverDiscloseRawValues() {
        val marker = "FOLDED_ANDROID_SECRET_1802"
        val input = "Authorization: Bearer\n  $marker\nsafe: context\nCookie: session=\n\t$marker"
        val outputs = listOf(
            SourceMetadataPolicy.title(input),
            SourceMetadataPolicy.channel(input),
            SourceMetadataPolicy.description(input),
            SourceMetadataPolicy.qualityLabel(input),
            SourceMetadataPolicy.diagnostic(input),
        )
        outputs.forEach { projected ->
            assertFalse(projected.contains(marker))
            assertTrue(projected.contains("[REDACTED_HEADER_CONTINUATION]"))
        }
        assertTrue(SourceMetadataPolicy.diagnostic(input).contains("safe: context"))
        // A second, explicitly named indented header must still expose its safe name.
        val named = SourceMetadataPolicy.diagnostic("Authorization: Bearer\n  Cookie: secret")
        assertTrue(named.contains("Cookie: [REDACTED]"))
        assertFalse(named.contains("secret"))
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

    @Test fun encodedQueryAndFragmentDelimitersInUrlPathsAreRedacted() {
        val marker = "ENCODED_PATH_PRIVATE_VALUE_1802"
        val inputs = listOf(
            "https://cdn.example/video%3Fsignature%3D$marker",
            "https://cdn.example/video%23private-$marker",
        )
        inputs.forEach { input ->
            val projected = listOf(
                SourceMetadataPolicy.title("Title $input"),
                SourceMetadataPolicy.channel("Channel $input"),
                SourceMetadataPolicy.description("Description $input"),
                SourceMetadataPolicy.qualityLabel("1080p $input"),
                SourceMetadataPolicy.diagnostic("GET $input"),
            )
            projected.forEach { value ->
                assertFalse(value.contains(marker))
                assertTrue(value.contains("[REDACTED_URL]"))
            }
        }
    }

    @Test fun multiplyEncodedUrlPathDelimitersNeverExposePrivateValues() {
        val marker = "MULTI_ENCODED_PRIVATE_VALUE_1802"
        val inputs = listOf(
            "https://cdn.example/video%253Fsignature%253D$marker",
            "https://cdn.example/video%25253Fsignature%25253D$marker",
            "https://cdn.example/video%252523private-$marker",
            "https://cdn.example/archive/https%253A%252F%252Fuser%253A$marker%2540inner.example",
        )
        inputs.forEach { input ->
            val projected = listOf(
                SourceMetadataPolicy.title("Title $input"),
                SourceMetadataPolicy.channel("Channel $input"),
                SourceMetadataPolicy.description("Description $input"),
                SourceMetadataPolicy.qualityLabel("1080p $input"),
                SourceMetadataPolicy.diagnostic("GET $input"),
            )
            projected.forEach { value ->
                assertFalse(value.contains(marker))
                assertTrue(value.contains("[REDACTED_URL]"))
            }
        }
        // Ordinary percent-encoded path components remain visible.
        assertTrue(SourceMetadataPolicy.diagnostic("GET https://cdn.example/media%20file").contains("media%2520file"))
    }

    @Test fun oversizedUntrustedTextFailsClosedBeforePresentationRedaction() {
        val marker = "PRIVATE_RAW_OVERFLOW_1802"
        val oversized = "https://user:$marker@cdn.example/video?sig=$marker" +
            "x".repeat(100_000)
        assertEquals("Untitled video", SourceMetadataPolicy.title(oversized))
        assertEquals("Unknown channel", SourceMetadataPolicy.channel(oversized))
        assertEquals("No description", SourceMetadataPolicy.description(oversized))
        assertEquals("Unknown quality", SourceMetadataPolicy.qualityLabel(oversized))
        assertEquals("Unknown error", SourceMetadataPolicy.diagnostic(oversized))
        // Normal-size signed metadata must still be redacted, not blanket-rejected.
        val ordinary = SourceMetadataPolicy.title("Preview https://cdn.example/video?sig=$marker")
        assertTrue(ordinary.contains("video?REDACTED"))
        assertFalse(ordinary.contains(marker))
    }

    @Test fun ordinaryDiagnosticTextIsPreserved() {
        assertEquals("download failed: connection reset", SourceMetadataPolicy.diagnostic("download failed: connection reset"))
    }
}
