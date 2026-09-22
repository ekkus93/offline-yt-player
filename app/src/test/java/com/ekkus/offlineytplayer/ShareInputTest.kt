package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.ui.DownloadSetupRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun rejectsOversizedUntrustedShareText() {
        val text = "x".repeat(8_193) + " https://example.com"
        assertNull(ShareInput.parse("android.intent.action.SEND", "text/plain", text))
    }

    @Test
    fun shareUrlRoutesIntoDownloadSetupPreview() {
        val sharedUrl = ShareInput.parse(
            "android.intent.action.SEND",
            "text/plain",
            "Save offline https://youtu.be/dQw4w9WgXcQ",
        )
        assertNotNull(sharedUrl)
        val setup = DownloadSetupRoute.previewFor(sharedUrl!!)
        requireNotNull(setup)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", setup.sourceUrl)
        assertEquals("Best compatible", setup.qualityLabel)
        assertTrue(setup.readyForDownload)
    }

    @Test
    fun malformedShareCannotReachDownloadSetupPreview() {
        assertNull(DownloadSetupRoute.previewFor("javascript:alert(1)"))
        assertNull(DownloadSetupRoute.previewFor(""))
    }
}
