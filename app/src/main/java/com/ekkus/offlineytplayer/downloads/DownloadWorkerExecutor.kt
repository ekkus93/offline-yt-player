package com.ekkus.offlineytplayer.downloads

import android.content.Context
import com.ekkus.offlineytplayer.core.FfiDownloadWorkerService
import com.ekkus.offlineytplayer.settings.SharedPreferencesAppSettingsStore
import java.io.File
internal class NativeWorkerStopGate<T : Any>(private val stopNative: (T) -> Unit) {
    private val lock = Any()
    private var stopped = false
    private var active: T? = null
    private var stopSignaled = false

    fun requestStop() = synchronized(lock) {
        stopped = true
        val handle = active
        if (handle != null && !stopSignaled) {
            stopSignaled = true
            stopNative(handle)
        }
    }

    fun attach(handle: T) = synchronized(lock) {
        check(active == null) { "native worker handle already attached" }
        active = handle
        stopSignaled = false
        if (stopped) {
            stopSignaled = true
            stopNative(handle)
        }
    }

    fun detach(handle: T) = synchronized(lock) {
        if (active === handle) {
            active = null
            stopSignaled = false
        }
    }
}

internal class DownloadWorkerExecution {
    // Attach, stop and detach serialize around the UniFFI handle. An Android
    // stop callback must never invoke requestPlatformStop on a closed handle.
    private val stopGate = NativeWorkerStopGate<FfiDownloadWorkerService> {
        it.requestPlatformStop()
    }

    fun requestStop() = stopGate.requestStop()

    fun execute(context: Context, queueItemId: String): Boolean = try {
        val databasePath = File(context.filesDir, "offline-yt-player.sqlite3").absolutePath
        val maxConcurrentDownloads = SharedPreferencesAppSettingsStore.open(context).use { store ->
            store.snapshot().maxConcurrentDownloads.toULong()
        }
        val service = FfiDownloadWorkerService.open(databasePath)
        try {
            stopGate.attach(service)
            // Use the generated Kotlin API directly. Methods accepting ULong have
            // mangled JVM names, so Java reflection looking for "executeJob" by
            // its unmangled name silently failed and left scheduled work QUEUED.
            val result = service.executeJob(
                queueItemId,
                System.currentTimeMillis().toULong(),
                maxConcurrentDownloads,
            )
            result.error == null && result.executed
        } finally {
            stopGate.detach(service)
            (service as? AutoCloseable)?.close()
        }
    } catch (_: Exception) {
        false
    }
}

internal object DownloadWorkerExecutor {
    fun execute(context: Context, queueItemId: String): Boolean =
        DownloadWorkerExecution().execute(context, queueItemId)
}
