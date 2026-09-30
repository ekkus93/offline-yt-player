package com.ekkus.offlineytplayer.downloads

import android.content.Context
import com.ekkus.offlineytplayer.settings.SharedPreferencesAppSettingsStore
import java.io.File
import java.lang.reflect.Method

internal object DownloadWorkerExecutor {
    private const val ProductionDatabaseName = "offline-yt-player.sqlite3"

    fun execute(context: Context, queueItemId: String): Boolean = try {
        val service = openGeneratedService(
            className = "com.ekkus.offlineytplayer.core.FfiDownloadWorkerService",
            databasePath = File(context.filesDir, ProductionDatabaseName).absolutePath,
        )
        val maxConcurrentDownloads = SharedPreferencesAppSettingsStore.open(context).use { store ->
            store.snapshot().maxConcurrentDownloads.toULong()
        }
        try {
            val execute = service.javaClass.methods.firstOrNull { method ->
                method.name == "executeJob" && method.parameterTypes.size == 3
            } ?: error("Generated FfiDownloadWorkerService does not expose executeJob/3")
            val result = execute.invoke(
                service,
                queueItemId,
                System.currentTimeMillis().toULong(),
                maxConcurrentDownloads,
            ) ?: error("Generated FfiDownloadWorkerService.executeJob returned null")
            readNullable(result, "error") == null && readBoolean(result, "executed")
        } finally {
            (service as? AutoCloseable)?.close()
        }
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: RuntimeException) {
        false
    }

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

    private fun readBoolean(target: Any, vararg names: String): Boolean =
        readNullable(target, *names) as? Boolean
            ?: error("Missing generated Boolean property ${names.joinToString("/")}")

    private fun readNullable(target: Any, vararg names: String): Any? {
        for (name in names) {
            target.javaClass.methods.firstOrNull {
                it.parameterTypes.isEmpty() &&
                    (it.name == name || it.name == "get${name.replaceFirstChar(Char::uppercase)}")
            }?.let { return it.invoke(target) }
            target.javaClass.declaredFields.firstOrNull { it.name == name }?.let { field ->
                field.isAccessible = true
                return field.get(target)
            }
        }
        return null
    }
}
