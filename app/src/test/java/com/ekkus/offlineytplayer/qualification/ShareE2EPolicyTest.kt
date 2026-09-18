package com.ekkus.offlineytplayer.qualification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareE2EPolicyTest {
    @Test
    fun requiredFlowCoversShareResolveDownloadAndLibraryVerification() {
        assertTrue(ShareE2EPolicy.accepts(ShareE2EStep.entries.toList()))
    }

    @Test
    fun missingShareOrLibraryVerificationFailsQualification() {
        assertFalse(ShareE2EPolicy.accepts(ShareE2EStep.entries.filterNot { it == ShareE2EStep.ReceiveAndroidShareUrl }))
        assertFalse(ShareE2EPolicy.accepts(ShareE2EStep.entries.filterNot { it == ShareE2EStep.VerifyCompletedLibraryItem }))
    }

    @Test
    fun sharedTextMustBeBoundedHttpsUrl() {
        assertTrue(ShareE2EPolicy.acceptsSharedText("https://example.invalid/watch?v=fixture"))
        assertFalse(ShareE2EPolicy.acceptsSharedText("http://example.invalid/watch"))
        assertFalse(ShareE2EPolicy.acceptsSharedText("not a url"))
        assertFalse(ShareE2EPolicy.acceptsSharedText("https://" + "x".repeat(2048)))
    }
}
