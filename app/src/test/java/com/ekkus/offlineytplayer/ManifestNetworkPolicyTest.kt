package com.ekkus.offlineytplayer

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestNetworkPolicyTest {
    private val manifest = Files.readString(Paths.get("src/main/AndroidManifest.xml"))

    @Test
    fun productionManifestDeclaresInternetAndNetworkStateCapabilities() {
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun productionManifestDoesNotRequestBroadStorageOrLocationPermissions() {
        val forbidden = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        )
        assertEquals(emptyList<String>(), forbidden.filter(manifest::contains))
    }
}
