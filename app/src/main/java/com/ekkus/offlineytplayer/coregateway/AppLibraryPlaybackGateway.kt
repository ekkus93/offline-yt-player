package com.ekkus.offlineytplayer.coregateway

import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.Future

data class CoreLibraryPlaybackAsset(
    val itemId: String,
    val videoRelativePath: String?,
    val audioRelativePath: String?,
    val playable: Boolean,
    val unavailableReason: String?,
)

interface AppLibraryPlaybackGateway : Closeable {
    fun listPlaybackAssets(): CoreGatewayResult<List<CoreLibraryPlaybackAsset>>
}

/**
 * Production gateway for library playback asset descriptors.
 *
 * The Rust core owns persistence and relative-path safety checks. Android only converts safe
 * relative paths into app-private files under filesDir before opening the canonical playback path.
 */
class GeneratedUniffiLibraryPlaybackGateway private constructor(
    private val ffiService: Any,
    private val dispatcher: CoreCallDispatcher,
) : AppLibraryPlaybackGateway {
    fun listPlaybackAssetsAsync(): Future<CoreGatewayResult<List<CoreLibraryPlaybackAsset>>> =
        dispatcher.submit { listPlaybackAssets() }

    override fun listPlaybackAssets(): CoreGatewayResult<List<CoreLibraryPlaybackAsset>> {
        checkNotMainThread()
        val result = callFfi("libraryPlaybackAssets")
        return CoreGatewayResult(
            value = readList(result, "assets").map(::mapPlaybackAsset),
            error = readPlaybackError(result),
        )
    }

    override fun close() {
        dispatcher.close()
    }

    private fun callFfi(methodName: String, vararg arguments: Any?): Any {
        val method = ffiService.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == arguments.size
        } ?: error("Generated FFI library playback service does not expose $methodName/${arguments.size}")
        return method.invoke(ffiService, *arguments)
            ?: error("Generated FFI library playback service returned null for $methodName")
    }

    companion object {
        fun open(
            databasePath: String,
            dispatcher: CoreCallDispatcher = CoreCallDispatcher.singleThreaded(),
        ): GeneratedUniffiLibraryPlaybackGateway {
            val service = openGeneratedService(databasePath)
            return GeneratedUniffiLibraryPlaybackGateway(service, dispatcher)
        }

        private fun openGeneratedService(databasePath: String): Any {
            val serviceClass = Class.forName("com.ekkus.offlineytplayer.core.FfiLibraryPlaybackService")
            serviceClass.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.let { method -> return method.invoke(null, databasePath) }

            val companion = serviceClass.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("Generated FfiLibraryPlaybackService has no static or companion open(databasePath)")
            val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
            val open: Method = companion.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("Generated FfiLibraryPlaybackService.Companion has no open(databasePath)")
            return open.invoke(companionInstance, databasePath)
                ?: error("Generated FfiLibraryPlaybackService.open returned null")
        }
    }
}

class FakeLibraryPlaybackGateway(
    initialAssets: List<CoreLibraryPlaybackAsset> = emptyList(),
) : AppLibraryPlaybackGateway {
    private val assets = initialAssets.associateBy { it.itemId }

    override fun listPlaybackAssets(): CoreGatewayResult<List<CoreLibraryPlaybackAsset>> =
        CoreGatewayResult(value = assets.values.sortedBy { it.itemId }, error = null)

    override fun close() = Unit
}

private fun mapPlaybackAsset(record: Any): CoreLibraryPlaybackAsset = CoreLibraryPlaybackAsset(
    itemId = readString(record, "itemId", "item_id"),
    videoRelativePath = readNullable(record, "videoRelativePath", "video_relative_path") as String?,
    audioRelativePath = readNullable(record, "audioRelativePath", "audio_relative_path") as String?,
    playable = readBoolean(record, "playable"),
    unavailableReason = readNullable(record, "unavailableReason", "unavailable_reason") as String?,
)

private fun readPlaybackError(result: Any): CoreGatewayError? {
    val message = readNullable(result, "errorMessage", "error_message") as String? ?: return null
    return CoreGatewayError(kind = "Persistence", message = message, retryable = false)
}

private fun readRequired(target: Any, vararg names: String): Any =
    readNullable(target, *names) ?: error("Missing generated property ${names.joinToString("/")}")

private fun readNullable(target: Any, vararg names: String): Any? {
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

@Suppress("UNCHECKED_CAST")
private fun readList(target: Any, vararg names: String): List<Any> = readRequired(target, *names) as List<Any>

private fun readString(target: Any, vararg names: String): String = readRequired(target, *names) as String

private fun readBoolean(target: Any, vararg names: String): Boolean = readRequired(target, *names) as Boolean
