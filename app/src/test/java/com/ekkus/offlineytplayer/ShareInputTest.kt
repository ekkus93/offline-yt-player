package com.ekkus.offlineytplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareInputTest {
    @Test
    fun acceptsHttpUrlFromPlainTextSend() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
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
    fun rejectsOversizedUntrustedShareTextBeforeTrimming() {
        val text = "x".repeat(8_193) + " https://example.com"
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", text))
        val paddedValidUrl = " ".repeat(1_000_000) + "https://youtu.be/dQw4w9WgXcQ"
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", paddedValidUrl))
    }

    @Test
    fun acceptsOneCanonicalShareWithoutFabricatingResolvedDownloadSetup() {
        val sharedUrl = ShareInput.parse(
            "android.intent.action.SEND",
            "text/plain",
            "Save offline https://youtu.be/dQw4w9WgXcQ",
        )
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", sharedUrl)
    }

    @Test
    fun rejectsUnsupportedOrAmbiguousSharedSources() {
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", "javascript:alert(1)"))
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", ""))
        assertNull(
            ShareInput.parse(
                "android.intent.action.SEND",
                "text/plain",
                "https://youtu.be/dQw4w9WgXcQ https://youtu.be/abcdefghijk",
            ),
        )
    }
}
