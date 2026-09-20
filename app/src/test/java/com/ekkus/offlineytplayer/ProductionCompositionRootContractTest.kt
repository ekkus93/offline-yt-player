package com.ekkus.offlineytplayer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCompositionRootContractTest {
    @Test
    fun mainActivityOwnsGeneratedGatewaysAndInjectsRepositoryState() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()

        assertTrue(source.contains("GeneratedUniffiCoreGateway.open(databasePath)"))
        assertTrue(source.contains("GeneratedUniffiDownloadControlGateway.open(databasePath)"))
        assertTrue(source.contains("libraryGateway.listLibrary()"))
        assertTrue(source.contains("libraryState = libraryState"))
        assertTrue(source.contains("downloadControlGateway = downloadControlGateway"))
        assertTrue(source.contains("override fun onDestroy()"))
        assertTrue(source.contains("coreGateway?.close()"))
        assertTrue(source.contains("downloadControlGateway?.close()"))
        assertFalse(source.contains("OfflineYTPlayerApp(initialSharedUrl = sharedUrl)"))
    }

    @Test
    fun generatedGatewayOpeningAndBlockingListRunOffMainThread() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        val threadStart = source.indexOf("Thread({")
        val coreOpen = source.indexOf("GeneratedUniffiCoreGateway.open(databasePath)")
        val listCall = source.indexOf("libraryGateway.listLibrary()")
        val uiHandoff = source.indexOf("runOnUiThread")

        assertTrue(threadStart >= 0)
        assertTrue(coreOpen > threadStart)
        assertTrue(listCall > coreOpen)
        assertTrue(uiHandoff > listCall)
    }
}
