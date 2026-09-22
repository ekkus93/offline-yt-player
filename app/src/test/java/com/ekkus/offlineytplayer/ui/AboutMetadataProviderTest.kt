package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutMetadataProviderTest {
    @Test
    fun versionLabelUsesBuildMetadata() {
        val metadata = AboutMetadata("1.2.3", 42, "abcdef1234567890", "licenses", "privacy", "diagnostics", "support")
        assertEquals("1.2.3 (42)", AboutMetadataProvider.versionLabel(metadata))
        assertEquals("abcdef123456", AboutMetadataProvider.revisionLabel(metadata))
    }

    @Test
    fun localRevisionIsExplicitWhenUnavailable() {
        val metadata = AboutMetadata("dev", 1, null, "licenses", "privacy", "diagnostics", "support")
        assertEquals("local build", AboutMetadataProvider.revisionLabel(metadata))
    }

    @Test
    fun currentMetadataAdvertisesConcreteSupportAndLegalInformation() {
        val metadata = AboutMetadataProvider.current()
        assertTrue(metadata.licenses.isNotBlank())
        assertTrue(metadata.privacy.isNotBlank())
        assertTrue(metadata.diagnostics.isNotBlank())
        assertTrue(metadata.support.contains("offline-yt-player"))
    }
}
