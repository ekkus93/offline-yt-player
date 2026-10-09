package com.ekkus.offlineytplayer.coregateway

import com.ekkus.offlineytplayer.core.ffiSavePlaybackPosition
import java.io.Closeable

interface AppPlaybackPositionGateway : Closeable {
    fun savePlaybackPosition(
        itemId: String,
        positionMs: Long,
        durationMs: Long?,
    ): CoreGatewayResult<Boolean>
}

/**
 * Production boundary for the generated UniFFI playback-position function.
 *
 * Use the Kotlin binding directly: ULong / ULong? arguments generate mangled JVM
 * method names, so reflective lookup by an unmangled name fails at runtime.
 */
class GeneratedUniffiPlaybackPositionGateway private constructor(
    private val databasePath: String,
) : AppPlaybackPositionGateway {
    override fun savePlaybackPosition(
        itemId: String,
        positionMs: Long,
        durationMs: Long?,
    ): CoreGatewayResult<Boolean> {
        checkNotMainThread()
        val result = ffiSavePlaybackPosition(
            databasePath,
            itemId,
            positionMs.coerceAtLeast(0L).toULong(),
            durationMs?.coerceAtLeast(0L)?.toULong(),
        )
        return CoreGatewayResult(
            value = result.saved,
            error = result.error?.let { error ->
                CoreGatewayError(
                    kind = error.kind.toString(),
                    message = error.message,
                    retryable = error.retryable,
                )
            },
        )
    }

    override fun close() = Unit

    companion object {
        fun open(databasePath: String): GeneratedUniffiPlaybackPositionGateway =
            GeneratedUniffiPlaybackPositionGateway(databasePath)
    }
}
