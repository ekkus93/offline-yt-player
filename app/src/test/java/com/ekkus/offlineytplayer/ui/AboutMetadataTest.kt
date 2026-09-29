package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutMetadataTest {
    @Test
    fun versionLabelUsesRuntimeVersionNameAndCode() {
        val metadata = metadata(
            versionName = "9.8.7-test",
            versionCode = 987,
        )

        assertEquals("9.8.7-test (987)", AboutMetadataProvider.versionLabel(metadata))
    }

    @Test
    fun revisionLabelUsesShortSourceRevisionWhenAvailable() {
        val metadata = metadata(sourceRevision = "0123456789abcdef")

        assertEquals("0123456789ab", AboutMetadataProvider.revisionLabel(metadata))
    }

    @Test
    fun revisionLabelFallsBackForLocalBuilds() {
        assertEquals("local build", AboutMetadataProvider.revisionLabel(metadata(sourceRevision = null)))
    }

    @Test
    fun runtimeMetadataIncludesUserVisibleSupportPrivacyAndDiagnosticsText() {
        val metadata = AboutMetadataProvider.current()

        assertFalse(metadata.versionName.isBlank())
        assertTrue(metadata.versionCode > 0)
        assertTrue(metadata.licenses.contains("Open-source", ignoreCase = true))
        assertTrue(metadata.privacy.contains("stored locally", ignoreCase = true))
        assertTrue(metadata.diagnostics.contains("exports", ignoreCase = true))
        assertTrue(metadata.support.contains("github.com/ekkus93/offline-yt-player"))
    }

    private fun metadata(
        versionName: String = "0.1.0-test",
        versionCode: Int = 1,
        sourceRevision: String? = null,
    ): AboutMetadata = AboutMetadata(
        versionName = versionName,
        versionCode = versionCode,
        sourceRevision = sourceRevision,
        licenses = "Open-source notices are distributed with the application and source repository.",
        privacy = "Offline media and application state are stored locally on this device.",
        diagnostics = "Diagnostics are local unless the user explicitly exports them.",
        support = "Support and source: github.com/ekkus93/offline-yt-player",
    )
}
