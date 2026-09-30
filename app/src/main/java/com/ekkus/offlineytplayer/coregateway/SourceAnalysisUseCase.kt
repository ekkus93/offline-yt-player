package com.ekkus.offlineytplayer.coregateway

import com.ekkus.offlineytplayer.SupportedUrlPolicy
import java.io.Closeable

/**
 * App-owned source-analysis use case.
 *
 * This boundary keeps URL validation, source resolution state mapping, and superseded-request
 * handling outside Compose widgets. Production UI can drive the same gateway through this use case
 * while deterministic JVM tests exercise the non-runtime policy without an emulator or live provider.
 */
class SourceAnalysisUseCase(
    private val gateway: AppSourceAnalysisGateway,
) : Closeable {
    private var nextTicketId: Long = 1L
    private var activeTicket: SourceAnalysisTicket? = null

    @Synchronized
    fun begin(sourceUrl: String): SourceAnalysisState {
        val normalized = SupportedUrlPolicy.normalizeSupportedUrl(sourceUrl)
            ?: return SourceAnalysisState.Unsupported("Enter a supported YouTube video URL")
        val ticket = SourceAnalysisTicket(nextTicketId++, normalized)
        activeTicket = ticket
        return SourceAnalysisState.Loading(ticket)
    }

    fun analyzeBlocking(sourceUrl: String): SourceAnalysisState {
        val loading = begin(sourceUrl)
        if (loading !is SourceAnalysisState.Loading) return loading
        return analyzeBlocking(loading.ticket)
    }

    fun analyzeBlocking(ticket: SourceAnalysisTicket): SourceAnalysisState =
        complete(ticket, gateway.analyze(ticket.normalizedUrl))

    @Synchronized
    fun complete(
        ticket: SourceAnalysisTicket,
        result: CoreGatewayResult<CoreSourceAnalysis>,
    ): SourceAnalysisState {
        if (activeTicket != ticket) return SourceAnalysisState.Superseded(ticket)
        activeTicket = null
        val error = result.error
        if (error != null) return classify(error)
        val analysis = result.value ?: return SourceAnalysisState.Failed(
            CoreGatewayError(
                kind = "EMPTY_SOURCE_ANALYSIS",
                message = "Source resolver returned no media.",
                retryable = false,
            ),
        )
        return SourceAnalysisState.Resolved(analysis)
    }

    @Synchronized
    fun cancelActive(): SourceAnalysisState {
        val ticket = activeTicket ?: return SourceAnalysisState.Idle
        activeTicket = null
        return SourceAnalysisState.Superseded(ticket)
    }

    override fun close() {
        gateway.close()
    }

    private fun classify(error: CoreGatewayError): SourceAnalysisState {
        val normalizedKind = error.kind.uppercase()
        return when {
            normalizedKind.contains("UNSUPPORTED") -> SourceAnalysisState.Unsupported(error.message)
            normalizedKind.contains("NETWORK") || normalizedKind.contains("TIMEOUT") -> SourceAnalysisState.NetworkFailure(error)
            normalizedKind.contains("SOURCE_CHANGED") || normalizedKind.contains("SOURCECHANGE") || normalizedKind.contains("PARSER") -> SourceAnalysisState.SourceChanged(error)
            else -> SourceAnalysisState.Failed(error)
        }
    }
}

data class SourceAnalysisTicket(
    val id: Long,
    val normalizedUrl: String,
)

sealed class SourceAnalysisState {
    object Idle : SourceAnalysisState()
    data class Loading(val ticket: SourceAnalysisTicket) : SourceAnalysisState()
    data class Resolved(val analysis: CoreSourceAnalysis) : SourceAnalysisState()
    data class Unsupported(val message: String) : SourceAnalysisState()
    data class NetworkFailure(val error: CoreGatewayError) : SourceAnalysisState()
    data class SourceChanged(val error: CoreGatewayError) : SourceAnalysisState()
    data class Failed(val error: CoreGatewayError) : SourceAnalysisState()
    data class Superseded(val ticket: SourceAnalysisTicket) : SourceAnalysisState()
}
