package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceAnalysisUseCaseTest {
    @Test
    fun unsupportedInputFailsBeforeCallingGateway() {
        val gateway = RecordingSourceGateway()
        val useCase = SourceAnalysisUseCase(gateway)

        val state = useCase.analyzeBlocking("not a supported source")

        assertTrue(state is SourceAnalysisState.Unsupported)
        assertEquals(emptyList<String>(), gateway.requests)
    }

    @Test
    fun resolvedSourceUsesGatewayResultAndPreservesCuratedChoices() {
        val gateway = RecordingSourceGateway(
            result = CoreGatewayResult(value = analysis(title = "Resolved fixture"), error = null),
        )
        val useCase = SourceAnalysisUseCase(gateway)

        val state = useCase.analyzeBlocking(supportedWatchUrl())

        assertTrue(state is SourceAnalysisState.Resolved)
        val resolved = state as SourceAnalysisState.Resolved
        assertEquals("Resolved fixture", resolved.analysis.title)
        assertEquals(listOf("720p", "Audio only"), resolved.analysis.qualityOptions.map { it.label })
        assertEquals(1, gateway.requests.size)
    }

    @Test
    fun sourceErrorsMapToActionableUseCaseStates() {
        assertTrue(analyzeError("NETWORK_FAILURE") is SourceAnalysisState.NetworkFailure)
        assertTrue(analyzeError("SOURCE_CHANGED") is SourceAnalysisState.SourceChanged)
        assertTrue(analyzeError("UNSUPPORTED_SOURCE") is SourceAnalysisState.Unsupported)
        assertTrue(analyzeError("UNKNOWN_FAILURE") is SourceAnalysisState.Failed)
    }

    @Test
    fun supersededRequestCannotPublishStaleResolution() {
        val useCase = SourceAnalysisUseCase(RecordingSourceGateway())
        val first = useCase.begin(supportedWatchUrl()) as SourceAnalysisState.Loading
        val second = useCase.begin(supportedShortUrl()) as SourceAnalysisState.Loading

        val stale = useCase.complete(first.ticket, CoreGatewayResult(value = analysis(title = "stale"), error = null))
        val current = useCase.complete(second.ticket, CoreGatewayResult(value = analysis(title = "current"), error = null))

        assertTrue(stale is SourceAnalysisState.Superseded)
        assertTrue(current is SourceAnalysisState.Resolved)
        assertEquals("current", (current as SourceAnalysisState.Resolved).analysis.title)
    }

    private fun analyzeError(kind: String): SourceAnalysisState {
        val useCase = SourceAnalysisUseCase(
            RecordingSourceGateway(
                result = CoreGatewayResult(
                    value = null,
                    error = CoreGatewayError(kind = kind, message = kind.lowercase(), retryable = false),
                ),
            ),
        )
        return useCase.analyzeBlocking(supportedWatchUrl())
    }

    private fun supportedWatchUrl(): String = "https://" + "www.youtube.com/watch?v=dQw4w9WgXcQ"

    private fun supportedShortUrl(): String = "https://" + "youtu.be/dQw4w9WgXcQ"

    private fun analysis(title: String): CoreSourceAnalysis = CoreSourceAnalysis(
        sourceUrl = supportedWatchUrl(),
        title = title,
        durationMs = 61_000,
        thumbnailUrl = null,
        qualityLabel = "720p",
        estimatedBytes = 1_000_000,
        qualityOptions = listOf(
            CoreSourceQualityChoice("720p", 1_000_000),
            CoreSourceQualityChoice("Audio only", 100_000),
        ),
    )

    private class RecordingSourceGateway(
        private val result: CoreGatewayResult<CoreSourceAnalysis> = CoreGatewayResult(
            value = analysis("default"),
            error = null,
        ),
    ) : AppSourceAnalysisGateway {
        val requests = mutableListOf<String>()

        override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
            requests += sourceUrl
            return result
        }

        override fun close() = Unit
    }
}
