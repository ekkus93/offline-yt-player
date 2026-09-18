package com.ekkus.offlineytplayer

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPermissionTest {
    private val manifest: String = Files.readString(Path.of("src/main/AndroidManifest.xml"))

    @Test
    fun networkPermissionsRequiredByProductionPathsAreDeclared() {
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun manifestDoesNotRequestBroadStorageOrLocationPermissions() {
        assertFalse(manifest.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
    }
}
