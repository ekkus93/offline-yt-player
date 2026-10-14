package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test
    fun decodedThumbnailBoundsRejectZeroNegativeAndExcessiveDimensions() {
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(0, 720))
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(-1, 720))
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(1280, 0))
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(4_097, 1))
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(1, 4_097))
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(4_096, 4_096))
        assertFalse(SetupThumbnailDecodePolicy.acceptsDimensions(Int.MAX_VALUE, Int.MAX_VALUE))
        assertTrue(SetupThumbnailDecodePolicy.acceptsDimensions(1_280, 720))
        assertTrue(SetupThumbnailDecodePolicy.acceptsDimensions(2_048, 2_048))
    }
}
