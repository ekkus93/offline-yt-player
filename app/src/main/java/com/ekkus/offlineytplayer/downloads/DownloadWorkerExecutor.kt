package com.ekkus.offlineytplayer.downloads

import android.content.Context
import com.ekkus.offlineytplayer.core.FfiDownloadWorkerService
import com.ekkus.offlineytplayer.settings.SharedPreferencesAppSettingsStore
import java.io.File

internal object DownloadWorkerExecutor {
    private const val ProductionDatabaseName = "offline-yt-player.sqlite3"

    fun execute(context: Context, queueItemId: String): Boolean = try {
        val databasePath = File(context.filesDir, ProductionDatabaseName).absolutePath
        val maxConcurrentDownloads = SharedPreferencesAppSettingsStore.open(context).use { store ->
            store.snapshot().maxConcurrentDownloads.toULong()
        }
        val service = FfiDownloadWorkerService.open(databasePath)
        try {
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
            (service as? AutoCloseable)?.close()
        }
    } catch (_: Exception) {
        false
    }
}
