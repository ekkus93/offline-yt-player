package com.ekkus.offlineytplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedUrlPolicyTest {
    @Test
    fun acceptsCoreSupportedYoutubeVideoForms() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            SupportedUrlPolicy.normalizeSupportedUrl(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
            ),
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?t=43",
            SupportedUrlPolicy.normalizeSupportedUrl("https://youtu.be/dQw4w9WgXcQ?t=43"),
        )
        assertEquals(
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            SupportedUrlPolicy.normalizeSupportedUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun rejectsUnsupportedSchemesHostsAndYoutubePages() {
        val rejected = listOf(
            "file:///tmp/video.mp4",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
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
    fun shareInputUsesSupportedUrlPolicy() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
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
    }
}
