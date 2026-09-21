package com.ekkus.offlineytplayer.coregateway

import java.io.Closeable
import java.lang.reflect.Method

interface AppPlaybackPositionGateway : Closeable {
    fun savePlaybackPosition(
        itemId: String,
        positionMs: Long,
        durationMs: Long?,
    ): CoreGatewayResult<Boolean>
}

/** Production boundary for the generated UniFFI playback-position function. */
class GeneratedUniffiPlaybackPositionGateway private constructor(
    private val databasePath: String,
) : AppPlaybackPositionGateway {
    override fun savePlaybackPosition(
        itemId: String,
        positionMs: Long,
        durationMs: Long?,
    ): CoreGatewayResult<Boolean> {
        checkNotMainThread()
        val result = saveMethod.invoke(
            null,
            databasePath,
            itemId,
            positionMs.coerceAtLeast(0L),
            durationMs?.coerceAtLeast(0L),
        ) ?: error("Generated playback-position FFI returned null")
        return CoreGatewayResult(
            value = readBooleanResult(result, "saved"),
            error = readGatewayError(result),
        )
    }

    override fun close() = Unit

    companion object {
        fun open(databasePath: String): GeneratedUniffiPlaybackPositionGateway =
            GeneratedUniffiPlaybackPositionGateway(databasePath)

        private val saveMethod: Method by lazy { findSaveMethod() }

        private fun findSaveMethod(): Method {
            val methodName = "ffiSavePlaybackPosition"
            val candidates = listOf(
                "com.ekkus.offlineytplayer.core.OfflineYtCoreKt",
                "com.ekkus.offlineytplayer.core.Offline_yt_coreKt",
                "com.ekkus.offlineytplayer.core.OfflineYtPlayerKt",
                "com.ekkus.offlineytplayer.core.Offline_yt_playerKt",
            )
            for (className in candidates) {
                val type = runCatching { Class.forName(className) }.getOrNull() ?: continue
                type.methods.firstOrNull { method ->
                    method.name == methodName && method.parameterTypes.size == 4
                }?.let { return it }
            }
            error("Generated UniFFI playback-position function $methodName was not found")
        }
    }
}

private fun readGatewayError(result: Any): CoreGatewayError? {
    val error = readNullableResult(result, "error") ?: return null
    return CoreGatewayError(
        kind = readRequiredResult(error, "kind").toString(),
        message = readStringResult(error, "message"),
        retryable = readBooleanResult(error, "retryable"),
    )
}

private fun readRequiredResult(target: Any, vararg names: String): Any =
    readNullableResult(target, *names) ?: error("Missing generated property ${names.joinToString("/")}")

private fun readNullableResult(target: Any, vararg names: String): Any? {
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

private fun readStringResult(target: Any, vararg names: String): String =
    readRequiredResult(target, *names) as String

private fun readBooleanResult(target: Any, vararg names: String): Boolean =
    readRequiredResult(target, *names) as Boolean
