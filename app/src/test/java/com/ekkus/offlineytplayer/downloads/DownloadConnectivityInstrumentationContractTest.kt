package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConnectivityInstrumentationContractTest {
    @Test
    fun instrumentationCoverageIsTrackedForConnectivityTransitions() {
        val source = File("src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadConnectivityInstrumentationTest.kt")
        assertTrue("RMD-508 connectivity instrumentation test is missing", source.isFile)
        val text = source.readText()
        assertTrue(text.contains("AndroidJUnit4"))
        assertTrue(text.contains("ConnectivityManager"))
        assertTrue(text.contains("DownloadConnectivity.None"))
        assertTrue(text.contains("DownloadConnectivity.Metered"))
        assertTrue(text.contains("DownloadConnectivity.Unmetered"))
        assertTrue(text.contains("DownloadConnectivityGateStatus.PausedForConnectivity"))
        assertTrue(text.contains("DownloadConnectivityGateStatus.StillWaiting"))
        assertTrue(text.contains("DownloadConnectivityGateStatus.Rescheduled"))
    }
}
