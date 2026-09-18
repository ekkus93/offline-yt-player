package com.ekkus.offlineytplayer.coregateway

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCoreGatewayPolicyTest {
    @Test
    fun fakeGatewaySupportsDeterministicRepositoryStateForUiTests() {
        val item = CoreLibraryItem(
            itemId = "item-1",
            source = CoreSourceIdentity("fixture", "media-1", "https://fixture.invalid/media-1"),
            displayTitle = "Fixture Item",
            durationMs = 1_000,
            qualityLabel = "720p",
            createdAtEpochMs = 7,
            playbackPositionMs = 0,
            completed = true,
        )
        val gateway = FakeCoreGateway(listOf(item))

        assertEquals(listOf(item), gateway.listLibrary("fixture").value)
        assertEquals(item, gateway.getLibraryItem("item-1").value)
        assertEquals(true, gateway.deleteLibraryItem("item-1").value)
        assertEquals(emptyList<CoreLibraryItem>(), gateway.listLibrary().value)
    }

    @Test
    fun productionGatewayIsTheOnlyLayerThatNamesGeneratedUniffiService() {
        val source = File("src/main/java/com/ekkus/offlineytplayer/coregateway/AppCoreGateway.kt").readText()

        assertTrue(source.contains("Class.forName(\"com.ekkus.offlineytplayer.core.FfiCoreService\")"))
        assertTrue(source.contains("CoreCallDispatcher"))
        assertTrue(source.contains("checkNotMainThread()"))
        assertTrue(source.contains("FakeCoreGateway"))
        assertTrue(source.contains("CoreGatewayError"))
        assertFalse(source.contains("onClick = {}"))
    }
}
