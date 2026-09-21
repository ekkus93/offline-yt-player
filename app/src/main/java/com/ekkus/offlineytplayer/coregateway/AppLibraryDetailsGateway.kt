package com.ekkus.offlineytplayer.coregateway

import java.io.Closeable
import java.lang.reflect.Method

data class CoreLibraryDetailAsset(
    val assetId: String,
    val kind: String,
    val relativePath: String,
    val bytes: Long,
    val mimeType: String?,
    val hasSha256: Boolean,
)

data class CoreLibraryDetails(
    val itemId: String,
    val provider: String,
    val mediaId: String,
    val canonicalUrl: String?,
    val displayTitle: String,
    val durationMs: Long?,
    val qualityLabel: String,
    val completed: Boolean,
    val playbackPositionMs: Long,
    val totalBytes: Long,
    val assets: List<CoreLibraryDetailAsset>,
)

interface AppLibraryDetailsGateway : Closeable {
    fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?>
}

class GeneratedUniffiLibraryDetailsGateway private constructor(
    private val ffiService: Any,
) : AppLibraryDetailsGateway {
    override fun getDetails(itemId: String): CoreGatewayResult<CoreLibraryDetails?> {
        checkNotMainThread()
        val result = callFfi("libraryDetails", itemId)
        val errorMessage = readNullableDetail(result, "errorMessage", "error_message") as String?
        val details = readNullableDetail(result, "details")?.let(::mapDetails)
        return CoreGatewayResult(
            value = details,
            error = errorMessage?.let { CoreGatewayError("library_details", it, false) },
        )
    }

    override fun close() = Unit

    private fun callFfi(methodName: String, vararg arguments: Any?): Any {
        val method = ffiService.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == arguments.size
        } ?: error("Generated library-details service does not expose $methodName/${arguments.size}")
        return method.invoke(ffiService, *arguments)
            ?: error("Generated library-details service returned null for $methodName")
    }

    companion object {
        fun open(databasePath: String): GeneratedUniffiLibraryDetailsGateway =
            GeneratedUniffiLibraryDetailsGateway(openGeneratedService(databasePath))

        private fun openGeneratedService(databasePath: String): Any {
            val serviceClass = Class.forName("com.ekkus.offlineytplayer.core.FfiLibraryDetailsService")
            serviceClass.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.let { return it.invoke(null, databasePath) }
            val companion = serviceClass.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("Generated FfiLibraryDetailsService has no open(databasePath)")
            val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
            val open: Method = companion.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("Generated FfiLibraryDetailsService.Companion has no open(databasePath)")
            return open.invoke(companionInstance, databasePath)
                ?: error("Generated FfiLibraryDetailsService.open returned null")
        }
    }
}

private fun mapDetails(record: Any): CoreLibraryDetails = CoreLibraryDetails(
    itemId = readStringDetail(record, "itemId", "item_id"),
    provider = readStringDetail(record, "provider"),
    mediaId = readStringDetail(record, "mediaId", "media_id"),
    canonicalUrl = readNullableDetail(record, "canonicalUrl", "canonical_url") as String?,
    displayTitle = readStringDetail(record, "displayTitle", "display_title"),
    durationMs = (readNullableDetail(record, "durationMs", "duration_ms") as Number?)?.toLong(),
    qualityLabel = readStringDetail(record, "qualityLabel", "quality_label"),
    completed = readBooleanDetail(record, "completed"),
    playbackPositionMs = readNumberDetail(record, "playbackPositionMs", "playback_position_ms").toLong(),
    totalBytes = readNumberDetail(record, "totalBytes", "total_bytes").toLong(),
    assets = readListDetail(record, "assets").map { asset ->
        CoreLibraryDetailAsset(
            assetId = readStringDetail(asset, "assetId", "asset_id"),
            kind = readStringDetail(asset, "kind"),
            relativePath = readStringDetail(asset, "relativePath", "relative_path"),
            bytes = readNumberDetail(asset, "bytes").toLong(),
            mimeType = readNullableDetail(asset, "mimeType", "mime_type") as String?,
            hasSha256 = readBooleanDetail(asset, "hasSha256", "has_sha256"),
        )
    },
)

private fun readNullableDetail(target: Any, vararg names: String): Any? {
    for (name in names) {
        target.javaClass.methods.firstOrNull { it.name == name || it.name == "get${name.replaceFirstChar(Char::uppercase)}" }
            ?.takeIf { it.parameterTypes.isEmpty() }
            ?.let { return it.invoke(target) }
        target.javaClass.declaredFields.firstOrNull { it.name == name }?.let { field ->
            field.isAccessible = true
            return field.get(target)
        }
    }
    return null
}

private fun readRequiredDetail(target: Any, vararg names: String): Any =
    readNullableDetail(target, *names) ?: error("Missing generated property ${names.joinToString("/")}")

@Suppress("UNCHECKED_CAST")
private fun readListDetail(target: Any, vararg names: String): List<Any> = readRequiredDetail(target, *names) as List<Any>
private fun readStringDetail(target: Any, vararg names: String): String = readRequiredDetail(target, *names) as String
private fun readBooleanDetail(target: Any, vararg names: String): Boolean = readRequiredDetail(target, *names) as Boolean
private fun readNumberDetail(target: Any, vararg names: String): Number = readRequiredDetail(target, *names) as Number
