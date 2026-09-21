package com.ekkus.offlineytplayer.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSettingsRuntimeConsumptionTest {
    @Test
    fun storageSettingsRenderRealBreakdownAndRequireCleanupConfirmation() {
        val shell = File("src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt").readText()
        assertTrue(shell.contains("StorageSettingsManager.open(context)"))
        assertTrue(shell.contains("summary.databaseBytes"))
        assertTrue(shell.contains("summary.partialBytes"))
        assertTrue(shell.contains("summary.cacheBytes"))
        assertTrue(shell.contains("Confirm destructive cleanup"))
        assertTrue(shell.contains("manager.cleanup(pending)"))
    }
}
