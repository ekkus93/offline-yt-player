package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSourceAnalysisGatewayMappingTest {
    private data class ContainerRecord(val containerOptions: Any)

    @Test
    fun requiredStringListPreservesGeneratedValues() {
        assertEquals(
            listOf("mp4", "webm"),
            readRequiredSourceStringList(ContainerRecord(listOf("mp4", "webm")), "containerOptions"),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun requiredStringListRejectsWrongPropertyType() {
        readRequiredSourceStringList(ContainerRecord("mp4"), "containerOptions")
    }

    @Test(expected = IllegalStateException::class)
    fun requiredStringListRejectsWrongElementType() {
        readRequiredSourceStringList(ContainerRecord(listOf("mp4", 7)), "containerOptions")
    }
}
