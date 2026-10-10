package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPresentationGatewayMappingTest {
    private data class PresentationListRecord(val items: List<String>)
    private data class WrongTypeRecord(val items: String)
    private class MissingRecord

    @Test
    fun requiredPresentationItemsPreserveGeneratedList() {
        assertEquals(
            listOf("one", "two"),
            readRequiredPresentationItems(PresentationListRecord(listOf("one", "two"))),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun requiredPresentationItemsRejectMissingProperty() {
        readRequiredPresentationItems(MissingRecord())
    }

    @Test(expected = IllegalStateException::class)
    fun requiredPresentationItemsRejectWrongType() {
        readRequiredPresentationItems(WrongTypeRecord("not-a-list"))
    }
}
