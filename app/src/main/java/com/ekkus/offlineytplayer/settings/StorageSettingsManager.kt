package com.ekkus.offlineytplayer.settings

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

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
    private val freeBytesProvider: (File) -> Long,
) {
    fun summarize(): ManagedStorageSummary {
        // File.walkTopDown() follows symbolic-link directories on some runtimes;
        // never read or count contents outside this app-owned tree.
        val files = filesRoot.managedEntries().filter(File::isManagedRegularFile)
        val database = files.filter(::isDatabaseFile).sumOf(File::length)
        val partial = files.filter(::isIncompleteFile).sumOf(File::length)
        val media = files.filterNot { isDatabaseFile(it) || isIncompleteFile(it) }.sumOf(File::length)
        return ManagedStorageSummary(media, database, partial, cacheRoot.managedSize(), freeBytesProvider(filesRoot))
    }

    fun cleanup(action: ManagedCleanup): Long {
        val root = if (action == ManagedCleanup.Cache) cacheRoot else filesRoot
        val targets = when (action) {
            ManagedCleanup.Cache -> cacheRoot.listFiles().orEmpty().toList()
            // Unlink incomplete-asset symlinks rather than following their targets.
            ManagedCleanup.Incomplete -> filesRoot.managedEntries().filter {
                isIncompleteFile(it) && (it.isManagedRegularFile() || Files.isSymbolicLink(it.toPath()))
            }
        }
        val bytes = targets.sumOf(File::managedSize)
        targets.forEach { deleteManagedEntry(it, root) }
        // Files.delete throws on any failure: never report a successful cleanup
        // while inaccessible or failed-to-delete files remain.
        return bytes
    }

    private fun deleteManagedEntry(target: File, root: File) {
        val normalizedRoot = root.toPath().toAbsolutePath().normalize()
        val normalizedTarget = target.toPath().toAbsolutePath().normalize()
        require(normalizedTarget != normalizedRoot && normalizedTarget.startsWith(normalizedRoot)) {
            "Managed cleanup target is outside its app-private root"
        }
        if (!Files.isSymbolicLink(normalizedTarget) &&
            Files.isDirectory(normalizedTarget, LinkOption.NOFOLLOW_LINKS)
        ) {
            val children = target.listFiles() ?: throw IOException("Cannot list managed cleanup directory")
            children.forEach { deleteManagedEntry(it, root) }
        }
        // Never traverse symbolic links. Files.delete removes a link itself,
        // not its external referent. Failures propagate to the UI.
        Files.delete(normalizedTarget)
    }

    private fun isDatabaseFile(file: File): Boolean =
        file.name == "offline-yt-player.sqlite3" || file.name.startsWith("offline-yt-player.sqlite3-")

    private fun isIncompleteFile(file: File): Boolean =
        file.name.endsWith(".partial") || file.name.endsWith(".resume.json")

    companion object {
        fun open(context: Context) = StorageSettingsManager(context.filesDir, context.cacheDir) { root ->
            StatFs(root.absolutePath).availableBytes
        }
        internal fun forTest(filesRoot: File, cacheRoot: File, freeBytes: Long = 0L) =
            StorageSettingsManager(filesRoot, cacheRoot) { freeBytes }
    }
}

private fun File.isManagedRegularFile(): Boolean =
    Files.isRegularFile(toPath(), LinkOption.NOFOLLOW_LINKS)

private fun File.managedEntries(): List<File> {
    if (Files.isSymbolicLink(toPath())) return listOf(this)
    if (!exists()) return emptyList()
    if (!isDirectory) return listOf(this)
    val children = listFiles() ?: throw IOException("Cannot enumerate managed storage")
    return listOf(this) + children.flatMap(File::managedEntries)
}

private fun File.managedSize(): Long =
    managedEntries().filter(File::isManagedRegularFile).sumOf(File::length)
