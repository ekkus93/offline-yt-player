package com.ekkus.offlineytplayer.coregateway

import android.os.Looper
import com.ekkus.offlineytplayer.SupportedUrlPolicy
import java.io.Closeable
import java.lang.reflect.Method

data class CoreSourceQualityChoice(
    val label: String,
    val estimatedBytes: Long?,
)

data class CoreSourceAnalysis(
    val sourceUrl: String,
    val title: String,
    val durationMs: Long?,
    val thumbnailUrl: String?,
    val qualityLabel: String,
    val estimatedBytes: Long?,
    val qualityOptions: List<CoreSourceQualityChoice> = emptyList(),
)

interface AppSourceAnalysisGateway : Closeable {
    fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis>
}

/** Production-only source analysis boundary backed by FfiYouTubeSourceService. */
class GeneratedUniffiSourceAnalysisGateway private constructor(
    private val sourceService: Any,
) : AppSourceAnalysisGateway {
    override fun analyze(sourceUrl: String): CoreGatewayResult<CoreSourceAnalysis> {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Source analysis is blocking and must run off the Android main thread"
        }
        val normalized = SupportedUrlPolicy.normalizeSupportedUrl(sourceUrl)
            ?: return CoreGatewayResult(
                null,
                CoreGatewayError(
                    "UNSUPPORTED_SOURCE",
                    "Enter a supported YouTube video URL",
                    false,
                ),
            )
        val token = newGeneratedObject("com.ekkus.offlineytplayer.core.FfiCancellationToken", "new")
        val resolved = callTwoArg(sourceService, "resolve", normalized, token)
        readSourceError(resolved)?.let { return CoreGatewayResult(null, it) }
        val media = readSourceRequired(resolved, "media")
        val choicesResult = callTwoArg(sourceService, "listChoices", normalized, token)
        readSourceError(choicesResult)?.let { return CoreGatewayResult(null, it) }
        val choices = readSourceList(choicesResult, "choices")
        val qualityOptions = choices.map { choice ->
            CoreSourceQualityChoice(
                label = SourceMetadataPolicy.qualityLabel(readSourceString(choice, "label")),
                estimatedBytes = (readSourceNullable(choice, "estimatedBytes", "estimated_bytes") as Number?)?.toLong(),
            )
        }.distinctBy { it.label }
        val preferred = qualityOptions.firstOrNull()
        return CoreGatewayResult(
            CoreSourceAnalysis(
                sourceUrl = canonicalSourceUrl(media) ?: normalized,
                title = SourceMetadataPolicy.title(readSourceString(media, "title")),
                durationMs = (readSourceNullable(media, "durationMs", "duration_ms") as Number?)?.toLong(),
                thumbnailUrl = readSourceNullable(media, "thumbnailUrl", "thumbnail_url") as String?,
                qualityLabel = preferred?.label ?: "No compatible format",
                estimatedBytes = preferred?.estimatedBytes,
                qualityOptions = qualityOptions,
            ),
            null,
        )
    }

    override fun close() = Unit

    companion object {
        fun open(): GeneratedUniffiSourceAnalysisGateway = GeneratedUniffiSourceAnalysisGateway(
            newGeneratedObject("com.ekkus.offlineytplayer.core.FfiYouTubeSourceService", "new"),
        )
    }
}

private fun canonicalSourceUrl(media: Any): String? {
    val source = readSourceNullable(media, "source") ?: return null
    return readSourceNullable(source, "canonicalUrl", "canonical_url") as String?
}

private fun newGeneratedObject(className: String, factoryName: String): Any {
    val type = Class.forName(className)
    type.methods.firstOrNull { it.name == factoryName && it.parameterTypes.isEmpty() }
        ?.let { return it.invoke(null) ?: error("$className.$factoryName returned null") }
    val companion = type.getDeclaredField("Companion").get(null)
    val factory: Method = companion.javaClass.methods.firstOrNull {
        it.name == factoryName && it.parameterTypes.isEmpty()
    } ?: error("$className has no $factoryName() factory")
    return factory.invoke(companion) ?: error("$className.$factoryName returned null")
}

private fun callTwoArg(target: Any, methodName: String, first: Any, second: Any): Any {
    val method = target.javaClass.methods.firstOrNull {
        it.name == methodName && it.parameterTypes.size == 2
    } ?: error("Generated source service does not expose $methodName")
    return method.invoke(target, first, second) ?: error("Generated source service returned null for $methodName")
}

private fun readSourceError(result: Any): CoreGatewayError? {
    val error = readSourceNullable(result, "error") ?: return null
    return CoreGatewayError(
        kind = readSourceRequired(error, "kind").toString(),
        message = DiagnosticRedaction.sanitize(readSourceString(error, "message")),
        retryable = readSourceRequired(error, "retryable") as Boolean,
    )
}

private fun readSourceRequired(target: Any, vararg names: String): Any =
    readSourceNullable(target, *names) ?: error("Missing generated property ${names.joinToString("/")}")

private fun readSourceNullable(target: Any, vararg names: String): Any? {
    for (name in names) {
        target.javaClass.methods.firstOrNull {
            it.name == name || it.name == "get${name.replaceFirstChar(Char::uppercase)}"
        }?.takeIf { it.parameterTypes.isEmpty() }?.let { return it.invoke(target) }
        target.javaClass.declaredFields.firstOrNull { it.name == name }?.let { field ->
            field.isAccessible = true
            return field.get(target)
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun readSourceList(target: Any, vararg names: String): List<Any> =
    readSourceRequired(target, *names) as List<Any>

private fun readSourceString(target: Any, vararg names: String): String =
    readSourceRequired(target, *names) as String
