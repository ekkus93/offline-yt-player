package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRepositoryGatewayContractTest {
    @Test
    fun listLibraryExposesRepositoryBackedListSearchAndDetail() {
        val gateway = FakeCoreGateway(
            initialItems = listOf(
                libraryItem(id = "item-beta", title = "Beta Lecture", createdAtEpochMs = 2_000),
                libraryItem(id = "item-alpha", title = "Alpha Fixture", createdAtEpochMs = 1_000),
            ),
        )

        val allItems = gateway.listLibrary().value.orEmpty()
        assertEquals(listOf("item-alpha", "item-beta"), allItems.map { it.itemId })

        val searched = gateway.listLibrary(query = "lecture").value.orEmpty()
        assertEquals(listOf("item-beta"), searched.map { it.itemId })

        val detail = gateway.getLibraryItem("item-alpha").value
        assertEquals("Alpha Fixture", detail?.displayTitle)
        assertEquals("fixture-alpha", detail?.source?.mediaId)
        assertTrue(detail?.completed == true)
    }

    @Test
    fun deleteLibraryItemUpdatesRepositoryStateAndReportsMissingItems() {
        val gateway = FakeCoreGateway(
            initialItems = listOf(libraryItem(id = "item-delete", title = "Delete Me")),
        )

        assertTrue(gateway.deleteLibraryItem("item-delete").value == true)
        assertNull(gateway.getLibraryItem("item-delete").value)
        assertTrue(gateway.listLibrary().value.orEmpty().isEmpty())

        assertFalse(gateway.deleteLibraryItem("item-delete").value == true)
    }

    private fun libraryItem(
        id: String,
        title: String,
        createdAtEpochMs: Long = 1_000,
    ): CoreLibraryItem = CoreLibraryItem(
        itemId = id,
        source = CoreSourceIdentity(
            provider = "fixture",
            mediaId = id.replace("item-", "fixture-"),
            canonicalUrl = null,
        ),
        displayTitle = title,
        durationMs = 61_000,
        qualityLabel = "720p",
        createdAtEpochMs = createdAtEpochMs,
        playbackPositionMs = 0,
        completed = true,
    )
}
