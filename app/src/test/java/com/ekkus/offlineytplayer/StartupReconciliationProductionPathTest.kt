package com.ekkus.offlineytplayer

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupReconciliationProductionPathTest {
    @Test
    fun mainActivityRunsStartupReconciliationBeforeInitialRepositoryReads() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/MainActivity.kt").readText()
        val reconcileIndex = source.indexOf("openedCore?.reconcileStartup()")
        val libraryIndex = source.indexOf("openedCore!!.listLibrary()")
        val queueIndex = source.indexOf("openedCore!!.listDownloadQueue()")

        assertTrue(reconcileIndex >= 0)
        assertTrue(libraryIndex > reconcileIndex)
        assertTrue(queueIndex > reconcileIndex)
        assertTrue(source.contains("Startup reconciliation failed:"))
        assertTrue(source.contains("coreGateway?.takeIf { startupFailure == null }"))
    }
}
