package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupThumbnailPolicyTest {
    @Test
    fun acceptsOnlyBoundedHttpsYouTubeThumbnailHosts() {
        assertEquals(
            "https://i.ytimg.com/vi/example/default.jpg",
            SetupThumbnailPolicy.acceptedUrl("https://i.ytimg.com/vi/example/default.jpg"),
        )
        assertEquals(
            "https://img.youtube.ytimg.com/example.jpg",
            SetupThumbnailPolicy.acceptedUrl("https://img.youtube.ytimg.com/example.jpg"),
        )
        assertNull(SetupThumbnailPolicy.acceptedUrl("http://i.ytimg.com/example.jpg"))
        assertNull(SetupThumbnailPolicy.acceptedUrl("https://ytimg.com.evil.invalid/example.jpg"))
        assertNull(SetupThumbnailPolicy.acceptedUrl("https://user@i.ytimg.com/example.jpg"))
        assertNull(SetupThumbnailPolicy.acceptedUrl("https://example.invalid/example.jpg"))
    }

    @Test
    fun rejectsOversizedThumbnailUrls() {
        assertNull(
            SetupThumbnailPolicy.acceptedUrl(
                "https://i.ytimg.com/" + "x".repeat(4_096),
            ),
        )
    }
}
