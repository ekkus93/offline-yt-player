package com.ekkus.offlineytplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedUrlPolicyTest {
    @Test
    fun acceptsCoreSupportedYoutubeVideoFormsAsCanonicalUrls() {
        val canonical = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(
            canonical,
            SupportedUrlPolicy.normalizeSupportedUrl(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            ),
        )
        assertEquals(
            canonical,
            SupportedUrlPolicy.normalizeSupportedUrl("https://youtu.be/dQw4w9WgXcQ?t=43"),
        )
        assertEquals(
            canonical,
            SupportedUrlPolicy.normalizeSupportedUrl("http://m.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            canonical,
            SupportedUrlPolicy.normalizeSupportedUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun rejectsUnsupportedSchemesHostsAndYoutubePages() {
        val rejected = listOf(
            "file:///tmp/video.mp4",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com./watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/@example",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ/extra",
            "https://www.youtube.com/watch?v=too-short",
            "https://user:pass@www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
        rejected.forEach { input -> assertNull(input, SupportedUrlPolicy.normalizeSupportedUrl(input)) }
    }

    @Test
    fun rejectsOversizedUrlsBeforeParsing() {
        val oversized = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&pad=" + "x".repeat(4_096)
        assertNull(SupportedUrlPolicy.normalizeSupportedUrl(oversized))
    }

    @Test
    fun shareInputRequiresExactlyOneSupportedUrlAndReturnsCanonicalUrl() {
        val canonical = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(
            canonical,
            ShareInput.parse(
                "android.intent.action.SEND",
                "text/plain",
                "watch https://youtu.be/dQw4w9WgXcQ",
            ),
        )
        assertNull(
            ShareInput.parse(
                "android.intent.action.SEND",
                "text/plain",
                "unsupported https://example.com/video.mp4",
            ),
        )
        assertNull(
            ShareInput.parse(
                "android.intent.action.SEND",
                "text/plain",
                "choose https://youtu.be/dQw4w9WgXcQ or https://youtu.be/abcdefghijk",
            ),
        )
    }

    @Test
    fun textExtractionDeduplicatesEquivalentCoreCanonicalForms() {
        assertEquals(
            listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
            SupportedUrlPolicy.supportedUrlsFromText(
                "<https://youtu.be/dQw4w9WgXcQ>, https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            ),
        )
    }
}
