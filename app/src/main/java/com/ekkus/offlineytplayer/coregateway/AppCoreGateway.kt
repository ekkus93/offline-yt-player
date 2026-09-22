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

enum class CoreDownloadState {
    QUEUED,
    RESOLVING,
    DOWNLOADING,
    PAUSED,
    RETRY_WAIT,
    FAILED,
    VERIFYING,
    COMPLETED,
    CANCELED,
    ;

    companion object {
        fun fromGeneratedName(name: String): CoreDownloadState {
            val normalized = name
                .substringAfterLast('.')
                .replace("_", "")
                .replace("-", "")
                .lowercase()
            return when (normalized) {
                "queued" -> QUEUED
                "resolving" -> RESOLVING
                "downloading" -> DOWNLOADING
                "paused" -> PAUSED
                "retrywait" -> RETRY_WAIT
                "failed" -> FAILED
                "verifying" -> VERIFYING
                "completed" -> COMPLETED
                "canceled", "cancelled" -> CANCELED
                else -> error("Unknown generated download state $name")
            }
        }
    }
}

data class CoreDownloadSnapshot(
    val jobId: String,
    val state: CoreDownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val attempt: Int,
    val retryAtEpochMs: Long?,
    val lastError: CoreGatewayError?,
)

data class CoreStartupReconciliation(
    val jobsRequeued: Int,
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
    fun reconcileStartup(): CoreGatewayResult<CoreStartupReconciliation>
    fun listLibrary(query: String? = null): CoreGatewayResult<List<CoreLibraryItem>>
    fun getLibraryItem(itemId: String): CoreGatewayResult<CoreLibraryItem?>
    fun deleteLibraryItem(itemId: String): CoreGatewayResult<Boolean>
    fun listDownloadQueue(): CoreGatewayResult<List<CoreDownloadSnapshot>>
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
    private val ffiStartupReconciliationService: Any,
    private val dispatcher: CoreCallDispatcher,
) : AppCoreGateway {
    fun reconcileStartupAsync(): Future<CoreGatewayResult<CoreStartupReconciliation>> =
        dispatcher.submit { reconcileStartup() }

    fun listLibraryAsync(query: String? = null): Future<CoreGatewayResult<List<CoreLibraryItem>>> =
        dispatcher.submit { listLibrary(query) }

    fun getLibraryItemAsync(itemId: String): Future<CoreGatewayResult<CoreLibraryItem?>> =
        dispatcher.submit { getLibraryItem(itemId) }

    fun deleteLibraryItemAsync(itemId: String): Future<CoreGatewayResult<Boolean>> =
        dispatcher.submit { deleteLibraryItem(itemId) }

    fun listDownloadQueueAsync(): Future<CoreGatewayResult<List<CoreDownloadSnapshot>>> =
        dispatcher.submit { listDownloadQueue() }

    override fun reconcileStartup(): CoreGatewayResult<CoreStartupReconciliation> {
        checkNotMainThread()
        val result = callFfi(ffiStartupReconciliationService, "startupReconcile")
        return CoreGatewayResult(
            value = CoreStartupReconciliation(
                jobsRequeued = readNumber(result, "jobsRequeued", "jobs_requeued").toInt(),
            ),
            error = readError(result),
        )
    }

    override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
        checkNotMainThread()
        val result = callFfi(ffiService, "libraryList", query)
        return CoreGatewayResult(
            value = readList(result, "items").map(::mapLibraryItem),
            error = readError(result),
        )
    }

    override fun getLibraryItem(itemId: String): CoreGatewayResult<CoreLibraryItem?> {
        checkNotMainThread()
        val result = callFfi(ffiService, "libraryGet", itemId)
        return CoreGatewayResult(
            value = readNullable(result, "item")?.let(::mapLibraryItem),
            error = readError(result),
        )
    }

    override fun deleteLibraryItem(itemId: String): CoreGatewayResult<Boolean> {
        checkNotMainThread()
        val result = callFfi(ffiService, "libraryDelete", itemId)
        return CoreGatewayResult(
            value = readBoolean(result, "deleted"),
            error = readError(result),
        )
    }

    override fun listDownloadQueue(): CoreGatewayResult<List<CoreDownloadSnapshot>> {
        checkNotMainThread()
        val result = callFfi(ffiService, "downloadQueue")
        return CoreGatewayResult(
            value = readList(result, "jobs").map(::mapDownloadSnapshot),
            error = readError(result),
        )
    }

    override fun close() {
        dispatcher.close()
    }

    private fun callFfi(target: Any, methodName: String, vararg arguments: Any?): Any {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.size == arguments.size
        } ?: error("Generated FFI service does not expose $methodName/${arguments.size}")
        return method.invoke(target, *arguments) ?: error("Generated FFI service returned null for $methodName")
    }

    companion object {
        fun open(
            databasePath: String,
            dispatcher: CoreCallDispatcher = CoreCallDispatcher.singleThreaded(),
        ): GeneratedUniffiCoreGateway {
            val service = openGeneratedService(databasePath)
            val startupReconciliationService = openGeneratedStartupReconciliationService(databasePath)
            return GeneratedUniffiCoreGateway(service, startupReconciliationService, dispatcher)
        }

        private fun openGeneratedService(databasePath: String): Any =
            openGeneratedService(
                className = "com.ekkus.offlineytplayer.core.FfiCoreService",
                databasePath = databasePath,
            )

        private fun openGeneratedStartupReconciliationService(databasePath: String): Any =
            openGeneratedService(
                className = "com.ekkus.offlineytplayer.core.FfiStartupReconciliationService",
                databasePath = databasePath,
            )

        private fun openGeneratedService(className: String, databasePath: String): Any {
            val serviceClass = Class.forName(className)
            serviceClass.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            }?.let { method -> return method.invoke(null, databasePath) }

            val companion = serviceClass.declaredClasses.firstOrNull { it.simpleName == "Companion" }
                ?: error("Generated $className has no static or companion open(databasePath)")
            val companionInstance = serviceClass.getDeclaredField("Companion").get(null)
            val open: Method = companion.methods.firstOrNull { method ->
                method.name == "open" && method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("Generated $className.Companion has no open(databasePath)")
            return open.invoke(companionInstance, databasePath)
                ?: error("Generated $className.open returned null")
        }
    }
}

class FakeCoreGateway(
    initialItems: List<CoreLibraryItem> = emptyList(),
    initialDownloads: List<CoreDownloadSnapshot> = emptyList(),
    private val startupReconciliation: CoreGatewayResult<CoreStartupReconciliation> = CoreGatewayResult(
        value = CoreStartupReconciliation(jobsRequeued = 0),
        error = null,
    ),
) : AppCoreGateway {
    private val items = initialItems.associateBy { it.itemId }.toMutableMap()
    private val downloads = initialDownloads.associateBy { it.jobId }.toMutableMap()

    override fun reconcileStartup(): CoreGatewayResult<CoreStartupReconciliation> = startupReconciliation

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

    override fun listDownloadQueue(): CoreGatewayResult<List<CoreDownloadSnapshot>> =
        CoreGatewayResult(value = downloads.values.sortedBy { it.jobId }, error = null)

    override fun close() = Unit
}

internal fun checkNotMainThread() {
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

private fun mapDownloadSnapshot(record: Any): CoreDownloadSnapshot = CoreDownloadSnapshot(
    jobId = readString(record, "jobId", "job_id"),
    state = CoreDownloadState.fromGeneratedName(readRequired(record, "state").toString()),
    bytesDownloaded = readNumber(record, "bytesDownloaded", "bytes_downloaded").toLong(),
    totalBytes = (readNullable(record, "totalBytes", "total_bytes") as Number?)?.toLong(),
    attempt = readNumber(record, "attempt").toInt(),
    retryAtEpochMs = (readNullable(record, "retryAtEpochMs", "retry_at_epoch_ms") as Number?)?.toLong(),
    lastError = readNullable(record, "lastError", "last_error")?.let { error ->
        CoreGatewayError(
            kind = readRequired(error, "kind").toString(),
            message = readString(error, "message"),
            retryable = readBoolean(error, "retryable"),
        )
    },
)

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
