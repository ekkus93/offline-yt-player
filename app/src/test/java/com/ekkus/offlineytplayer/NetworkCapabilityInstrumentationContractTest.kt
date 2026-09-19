package com.ekkus.offlineytplayer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkCapabilityInstrumentationContractTest {
    @Test
    fun rmd101DeviceSmokeCoverageRemainsTracked() {
        val source = File("src/androidTest/java/com/ekkus/offlineytplayer/NetworkCapabilitySmokeTest.kt")
        assertTrue("RMD-101 device network smoke test is missing", source.isFile)
        val text = source.readText()
        assertTrue(text.contains("AndroidJUnit4"))
        assertTrue(text.contains("ServerSocket"))
        assertTrue(text.contains("127.0.0.1"))
        assertTrue(text.contains("openConnection"))
        assertTrue(text.contains("offline-yt-network-ok"))
    }
}
