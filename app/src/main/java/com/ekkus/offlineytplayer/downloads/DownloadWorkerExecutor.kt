package com.ekkus.offlineytplayer.downloads

import android.content.Context
import com.ekkus.offlineytplayer.core.FfiDownloadWorkerService
import java.io.File

internal object DownloadWorkerExecutor {
    fun execute(context: Context, queueItemId: String): Boolean = try {
        FfiDownloadWorkerService(File(context.filesDir, "library.sqlite3").absolutePath).use { worker ->
            val result = worker.executeJob(queueItemId, System.currentTimeMillis().toULong())
            result.error == null && result.executed
        }
    } catch (_: RuntimeException) {
        false
    }
}
