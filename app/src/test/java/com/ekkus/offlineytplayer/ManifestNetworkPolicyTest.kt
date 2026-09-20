package com.ekkus.offlineytplayer

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertEquals(emptyList(), forbidden.filter(manifest::contains))
    }
}
