package com.ekkus.offlineytplayer.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UntrustedInputPolicyTest {
    @Test
    fun sourceUrlsRequireHttpsHostAndNoEmbeddedCredentials() {
        assertTrue(UntrustedInputPolicy.validateSourceUrl("https://example.com/watch?v=1"))
        assertFalse(UntrustedInputPolicy.validateSourceUrl("http://example.com/watch?v=1"))
        assertFalse(UntrustedInputPolicy.validateSourceUrl("https://user:pass@example.com/watch"))
        assertFalse(UntrustedInputPolicy.validateSourceUrl("file:///etc/passwd"))
    }

    @Test
    fun metadataDropsControlCharactersAndIsBounded() {
        assertEquals("title  injected", UntrustedInputPolicy.sanitizeMetadata(" title\n\tinjected "))
        assertEquals(512, UntrustedInputPolicy.sanitizeMetadata("x".repeat(600)).length)
    }

    @Test
    fun traversalAndAbsolutePathsAreRejected() {
        assertTrue(UntrustedInputPolicy.isSafeRelativePath("media/video.mp4"))
        assertFalse(UntrustedInputPolicy.isSafeRelativePath("../secret"))
        assertFalse(UntrustedInputPolicy.isSafeRelativePath("media/../secret"))
        assertFalse(UntrustedInputPolicy.isSafeRelativePath("/absolute/file"))
        assertFalse(UntrustedInputPolicy.isSafeRelativePath("media//file"))
    }

    @Test
    fun filenamesAreReducedToSafeLeafNames() {
        assertEquals("passwd", UntrustedInputPolicy.sanitizeFilename("../../etc/passwd"))
        assertEquals("bad_name_.mp4", UntrustedInputPolicy.sanitizeFilename("bad:name?.mp4"))
        assertEquals("download", UntrustedInputPolicy.sanitizeFilename("..."))
    }
}
