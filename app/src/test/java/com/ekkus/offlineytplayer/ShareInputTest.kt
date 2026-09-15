package com.ekkus.offlineytplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareInputTest {
    @Test
    fun acceptsHttpUrlFromPlainTextSend() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            ShareInput.parse(
                "android.intent.action.SEND",
                "text/plain",
                "Watch this https://youtu.be/dQw4w9WgXcQ",
            ),
        )
    }

    @Test
    fun rejectsWrongIntentMimeAndMalformedInput() {
        assertNull(ShareInput.parse("android.intent.action.VIEW", "text/plain", "https://example.com"))
        assertNull(ShareInput.parse("android.intent.action.SEND", "image/png", "https://example.com"))
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", "not-a-url"))
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", "javascript:alert(1)"))
    }

    @Test
    fun rejectsOversizedUntrustedShareText() {
        val text = "x".repeat(8_193) + " https://example.com"
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", text))
    }
}
