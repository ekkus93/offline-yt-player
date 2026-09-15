package com.ekkus.offlineytplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildMetadataTest {
    @Test
    fun buildMetadataIsDisplayable() {
        assertFalse(BuildMetadata.version.isBlank())
        assertFalse(BuildMetadata.revision.isBlank())
        assertTrue(BuildMetadata.display.contains(BuildMetadata.version))
    }

    @Test
    fun artifactNameIsStableAndFilesystemSafe() {
        val name = BuildMetadata.artifactName()
        assertTrue(name.startsWith("offline-yt-player-"))
        assertTrue(name.endsWith(".apk"))
        assertFalse(name.contains(' '))
        assertFalse(name.contains('/'))
    }
}
