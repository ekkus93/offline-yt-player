package com.ekkus.offlineytplayer.settings

import android.content.Context
import android.os.StatFs
import java.io.File

internal data class ManagedStorageSummary(
    val mediaBytes: Long,
    val databaseBytes: Long,
    val partialBytes: Long,
    val cacheBytes: Long,
    val freeBytes: Long,
)

internal enum class ManagedCleanup { Cache, Incomplete }

internal class StorageSettingsManager private constructor(
    private val filesRoot: File,
    private val cacheRoot: File,
) {
    fun summarize(): ManagedStorageSummary {
        val files = filesRoot.walkTopDown().filter { it.isFile }.toList()
        val database = files.filter(::isDatabaseFile).sumOf(File::length)
        val partial = files.filter(::isIncompleteFile).sumOf(File::length)
        val media = files.filterNot { isDatabaseFile(it) || isIncompleteFile(it) }.sumOf(File::length)
        return ManagedStorageSummary(media, database, partial, cacheRoot.safeSize(), StatFs(filesRoot.absolutePath).availableBytes)
    }

    fun cleanup(action: ManagedCleanup): Long {
        val targets = when (action) {
            ManagedCleanup.Cache -> cacheRoot.listFiles().orEmpty().toList()
            ManagedCleanup.Incomplete -> filesRoot.walkTopDown().filter { it.isFile && isIncompleteFile(it) }.toList()
        }
        val bytes = targets.sumOf { if (it.isFile) it.length() else it.safeSize() }
        targets.forEach { target ->
            val root = if (action == ManagedCleanup.Cache) cacheRoot else filesRoot
            require(target.canonicalPath.startsWith(root.canonicalPath + File.separator))
            if (target.isDirectory) target.deleteRecursively() else target.delete()
        }
        return bytes
    }

    private fun isDatabaseFile(file: File): Boolean =
        file.name == "offline-yt-player.sqlite3" || file.name.startsWith("offline-yt-player.sqlite3-")

    private fun isIncompleteFile(file: File): Boolean =
        file.name.endsWith(".partial") || file.name.endsWith(".resume.json")

    companion object {
        fun open(context: Context) = StorageSettingsManager(context.filesDir, context.cacheDir)
        internal fun forTest(filesRoot: File, cacheRoot: File) = StorageSettingsManager(filesRoot, cacheRoot)
    }
}

private fun File.safeSize(): Long =
    if (!exists()) 0 else walkTopDown().filter { it.isFile }.sumOf(File::length)
