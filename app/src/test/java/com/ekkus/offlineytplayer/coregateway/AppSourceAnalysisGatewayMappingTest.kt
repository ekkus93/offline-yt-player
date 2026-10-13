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

    @Test
    fun sameDisplayLabelDoesNotDiscardSeparateProviderFormatChoices() {
        val choices = listOf(
            CoreSourceQualityChoice("720p", 10L, choiceId = "h264-720"),
            CoreSourceQualityChoice("720p", 11L, choiceId = "vp9-720"),
            CoreSourceQualityChoice("1080p", 15L, choiceId = "av1-1080"),
        )
        val displayed = disambiguateSourceQualityLabels(choices)
        assertEquals(listOf("720p", "720p (2)", "1080p"), displayed.map { it.label })
        assertEquals(listOf("h264-720", "vp9-720", "av1-1080"), displayed.map { it.choiceId })
        assertEquals(listOf(10L, 11L, 15L), displayed.map { it.estimatedBytes })
    }

    @Test
    fun generatedSuffixNeverCollidesWithLaterGenuineProviderLabel() {
        val choices = listOf(
            CoreSourceQualityChoice("720p", null, choiceId = "first"),
            CoreSourceQualityChoice("720p", null, choiceId = "second"),
            CoreSourceQualityChoice("720p (2)", null, choiceId = "third"),
            CoreSourceQualityChoice("720p", null, choiceId = "fourth"),
        )
        val displayed = disambiguateSourceQualityLabels(choices)
        assertEquals(listOf("720p", "720p (3)", "720p (2)", "720p (4)"), displayed.map { it.label })
        assertEquals(4, displayed.map { it.label }.distinct().size)
        assertEquals(choices.map { it.choiceId }, displayed.map { it.choiceId })
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
