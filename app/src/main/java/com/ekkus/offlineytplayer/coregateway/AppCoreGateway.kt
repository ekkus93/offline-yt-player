package com.ekkus.offlineytplayer.coregateway

import android.os.Looper
import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Lifecycle-owned dispatcher for blocking Rust/UniFFI calls.
 *
 * UI/ViewModel code must use [submit] instead of calling generated UniFFI service methods on the
 * Android main thread. The dispatcher is intentionally tiny so future coroutine or WorkManager
 * integration can replace it without leaking generated binding types into presentation code.
 */
class CoreCallDispatcher private constructor(
    private val executor: ExecutorService,
) : Closeable {
    fun <T> submit(call: () -> T): Future<T> = executor.submit(Callable { call() })

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        fun singleThreaded(): CoreCallDispatcher = CoreCallDispatcher(
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "offline-yt-core-gateway").apply { isDaemon = true }
            },
        )
    }
}

data class CoreSourceIdentity(
    val provider: String,
    val mediaId: String,
    val canonicalUrl: String?,
)

data class CoreLibraryItem(
    val itemId: String,
    val source: CoreSourceIdentity,
    val displayTitle: String,
    val durationMs: Long?,
    val qualityLabel: String,
    val createdAtEpochMs: Long,
    val playbackPositionMs: Long,
    val completed: Boolean,
)

data class CoreGatewayError(
    val kind: String,
    val message: String,
    val retryable: Boolean,
)

data class CoreGatewayResult<T>(
    val value: T?,
    val error: CoreGatewayError?,
) {
    val isSuccess: Boolean get() = error == null
}

interface AppCoreGateway : Closeable {
    fun listLibrary(query: String? = null): CoreGatewayResult<List<CoreLibraryItem>>
    fun getLibraryItem(itemId: String): CoreGatewayResult<CoreLibraryItem?>
    fun deleteLibraryItem(itemId: String): CoreGatewayResult<Boolean>
}

/**
 * Production gateway for generated UniFFI bindings.
 *
 * Generated classes live in `com.ekkus.offlineytplayer.core`; this adapter is the only production
 * layer that knows that package. Conversion from generated record/error objects into app models is
 * centralized here so ViewModels and repositories can remain generated-binding agnostic.
 */
class GeneratedUniffiCoreGateway private constructor(
    private val ffiService: Any,
    private val dispatcher: CoreCallDispatcher,
) : AppCoreGateway {
    fun listLibraryAsync(query: String? = null): Future<CoreGatewayResult<List<CoreLibraryItem>>> =
        dispatcher.submit { listLibrary(query) }

    fun getLibraryItemAsync(itemId: String): Future<CoreGatewayResult<CoreLibraryItem?>> =
        dispatcher.submit { getLibraryItem(itemId) }

    fun deleteLibraryItemAsync(itemId: String): Future<CoreGatewayResult<Boolean>> =
        dispatcher.submit { deleteLibraryItem(itemId) }

    override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
        checkNotMainThread()
        val result = callFfi("libraryList", query)
        return CoreGatewayResult(
            value = readList(result, "items").map(::mapLibraryItem),
            error = readError(result),
        )
    }

    override fun getLibraryItem(itemId: String): CoreGatewayResult<CoreLibraryItem?> {
        checkNotMainThread()
        val result = callFfi("libraryGet", itemId)
        return CoreGatewayResult(
            value = readNullable(result, "item")?.let(::mapLibraryItem),
            error = readError(result),
        )
    }

    override fun deleteLibraryItem(itemId: String): CoreGatewayResult<Boolean> {
        checkNotMainThread()
        val result = callFfi("libraryDelete", itemId)
        return CoreGatewayResult(
            value = readBoolean(result, "deleted"),
            error = readError(result),
        )
    }

    override fun close() {
        dispatcher.close()
    }

    private fun callFfi(methodName: String, argument: Any?): Any {
        val method = ffiService.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == 1
        } ?: error("Generated FFI service does not expose $methodName")
        return method.invoke(ffiService, argument) ?: error("Generated FFI service returned null for $methodName")
    }

    companion object {
        fun open(
            databasePath: String,
            dispatcher: CoreCallDispatcher = CoreCallDispatcher.singleThreaded(),
        ): GeneratedUniffiCoreGateway {
            val service = openGeneratedService(databasePath)
            return GeneratedUniffiCoreGateway(service, dispatcher)
        }

        private fun openGeneratedService(databasePath: String): Any {
            val serviceClass = Class.forName("com.ekkus.offlineytplayer.core.FfiCoreService")
            serviceClass.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.let { method -> return method.invoke(null, databasePath) }

            val companion = serviceClass.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("Generated FfiCoreService has no static or companion open(databasePath)")
            val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
            val open: Method = companion.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("Generated FfiCoreService.Companion has no open(databasePath)")
            return open.invoke(companionInstance, databasePath)
                ?: error("Generated FfiCoreService.open returned null")
        }
    }
}

class FakeCoreGateway(
    initialItems: List<CoreLibraryItem> = emptyList(),
) : AppCoreGateway {
    private val items = initialItems.associateBy { it.itemId }.toMutableMap()

    override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
        val normalized = query?.trim()?.lowercase().orEmpty()
        val listed = items.values
            .filter { normalized.isEmpty() || it.displayTitle.lowercase().contains(normalized) }
            .sortedBy { it.createdAtEpochMs }
        return CoreGatewayResult(value = listed, error = null)
    }

    override fun getLibraryItem(itemId: String): CoreGatewayResult<CoreLibraryItem?> =
        CoreGatewayResult(value = items[itemId], error = null)

    override fun deleteLibraryItem(itemId: String): CoreGatewayResult<Boolean> =
        CoreGatewayResult(value = items.remove(itemId) != null, error = null)

    override fun close() = Unit
}

private fun checkNotMainThread() {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "Core gateway calls are blocking and must be dispatched off the Android main thread"
    }
}

private fun mapLibraryItem(record: Any): CoreLibraryItem {
    val source = readRequired(record, "source")
    return CoreLibraryItem(
        itemId = readString(record, "itemId", "item_id"),
        source = CoreSourceIdentity(
            provider = readString(source, "provider"),
            mediaId = readString(source, "mediaId", "media_id"),
            canonicalUrl = readNullable(source, "canonicalUrl", "canonical_url") as String?,
        ),
        displayTitle = readString(record, "displayTitle", "display_title"),
        durationMs = (readNullable(record, "durationMs", "duration_ms") as Number?)?.toLong(),
        qualityLabel = readString(record, "qualityLabel", "quality_label"),
        createdAtEpochMs = readNumber(record, "createdAtEpochMs", "created_at_epoch_ms").toLong(),
        playbackPositionMs = readNumber(record, "playbackPositionMs", "playback_position_ms").toLong(),
        completed = readBoolean(record, "completed"),
    )
}

private fun readError(result: Any): CoreGatewayError? {
    val error = readNullable(result, "error") ?: return null
    return CoreGatewayError(
        kind = readRequired(error, "kind").toString(),
        message = readString(error, "message"),
        retryable = readBoolean(error, "retryable"),
    )
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

private fun readNumber(target: Any, vararg names: String): Number = readRequired(target, *names) as Number
