package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadPresentationGatewayMappingTest {
    private data class PresentationListRecord(val items: List<String>)
    private data class WrongTypeRecord(val items: String)
    private data class PresentationItem(val jobId: String, val displayTitle: String)
    private data class BadTitleItem(val jobId: String, val displayTitle: Int)
    private data class ErrorRecord(val error: String?)
    private class MissingRecord

    @Test
    fun nullablePresentationErrorPropertyAcceptsExplicitNull() {
        assertNull(readRequiredNullablePresentationError(ErrorRecord(null)))
        assertEquals("failed", readRequiredNullablePresentationError(ErrorRecord("failed")))
    }

    @Test(expected = IllegalStateException::class)
    fun nullablePresentationErrorPropertyRejectsMissingBinding() {
        readRequiredNullablePresentationError(MissingRecord())
    }

    @Test
    fun requiredPresentationItemsPreserveGeneratedList() {
        assertEquals(
            listOf("one", "two"),
            readRequiredPresentationItems(PresentationListRecord(listOf("one", "two"))),
        )
    }

    @Test
    fun validPresentationItemsProduceExactJobTitleMap() {
        assertEquals(mapOf("job-1" to "Video one"), mapRequiredPresentationItems(listOf(PresentationItem("job-1", "Video one"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullPresentationItemCannotBeSilentlyDropped() {
        mapRequiredPresentationItems(listOf(null))
    }

    @Test(expected = IllegalStateException::class)
    fun duplicatePresentationJobIdsCannotSilentlyOverwriteTitles() {
        mapRequiredPresentationItems(listOf(PresentationItem("job-1", "One"), PresentationItem("job-1", "Two")))
    }

    @Test(expected = IllegalStateException::class)
    fun wrongTypedPresentationTitleIsRejected() {
        mapRequiredPresentationItems(listOf(BadTitleItem("job-1", 42)))
    }

    @Test(expected = IllegalStateException::class)
    fun blankPresentationTitleIsRejected() {
        mapRequiredPresentationItems(listOf(PresentationItem("job-1", "")))
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
