package com.ekkus.offlineytplayer.settings

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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

internal interface ManagedStorageOperations {
    fun summarize(): ManagedStorageSummary
    fun cleanup(action: ManagedCleanup): Long
}

internal class StorageSettingsManager private constructor(
    private val filesRoot: File,
    private val cacheRoot: File,
    private val freeBytesProvider: (File) -> Long,
) : ManagedStorageOperations {
    override fun summarize(): ManagedStorageSummary {
        requireRealManagedRoot(filesRoot)
        requireRealManagedRoot(cacheRoot)
        // File.walkTopDown() follows symbolic-link directories on some runtimes;
        // never read or count contents outside this app-owned tree.
        var database = 0L
        var partial = 0L
        var media = 0L
        // Stream each app-owned file exactly once; do not materialize and rescan
        // the entire storage tree while calculating three category totals.
        filesRoot.managedEntries().forEach { file ->
            if (!file.isManagedRegularFile()) return@forEach
            val bytes = file.length()
            when {
                isDatabaseFile(file) -> database += bytes
                isIncompleteFile(file) -> partial += bytes
                else -> media += bytes
            }
        }
        return ManagedStorageSummary(media, database, partial, cacheRoot.managedSize(), freeBytesProvider(filesRoot))
    }

    override fun cleanup(action: ManagedCleanup): Long {
        val root = if (action == ManagedCleanup.Cache) cacheRoot else filesRoot
        // A symlinked cleanup root turns an external directory into an apparent
        // app-private cleanup boundary. Reject it before enumerating children,
        // including cacheRoot.listFiles(), which otherwise follows the link.
        requireRealManagedRoot(root)
        var bytes = 0L
        when (action) {
            ManagedCleanup.Cache -> {
                // DirectoryStream avoids allocating an array of every cache
                // child before the first cleanup operation can start.
                Files.newDirectoryStream(cacheRoot.toPath()).use { entries ->
                    for (entry in entries) {
                        val target = entry.toFile()
                        bytes += target.managedSize()
                        deleteManagedEntry(target, root)
                    }
                }
            }
            ManagedCleanup.Incomplete -> {
                // Unlink incomplete-asset symlinks without following targets.
                for (target in filesRoot.managedEntries().filter {
                    isIncompleteFile(it) && (it.isManagedRegularFile() || Files.isSymbolicLink(it.toPath()))
                }) {
                    bytes += target.managedSize()
                    deleteManagedEntry(target, root)
                }
            }
        }
        // Files.delete throws on any failure: never report a successful cleanup
        // while inaccessible or failed-to-delete files remain.
        return bytes
    }

    private fun requireRealManagedRoot(root: File) {
        if (Files.isSymbolicLink(root.toPath()) ||
            !Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("Managed storage root must be a real directory")
        }
    }

    private fun deleteManagedEntry(target: File, root: File) {
        val normalizedRoot = root.toPath().toAbsolutePath().normalize()
        fun owned(path: Path): Path {
            val normalized = path.toAbsolutePath().normalize()
            require(normalized != normalizedRoot && normalized.startsWith(normalizedRoot)) {
                "Managed cleanup target is outside its app-private root"
            }
            return normalized
        }
        // Files.walkFileTree uses a lazy iterator stack, not eager child arrays.
        // The default walk does NOT follow symbolic links: visitFile unlinks
        // owned symlinks rather than entering their external target directories.
        Files.walkFileTree(owned(target.toPath()), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                owned(dir)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(owned(file))
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(owned(dir))
                return FileVisitResult.CONTINUE
            }
        })
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

private fun File.managedEntries(): Sequence<File> = sequence {
    // Files.walk lazily enumerates each entry without following symlinks.
    // It avoids retaining every child path of a wide managed directory in
    // an explicit pending stack; close the stream on completion or failure.
    Files.walk(this@managedEntries.toPath()).use { paths ->
        for (path in paths.iterator()) {
            yield(path.toFile())
        }
    }
}

private fun File.managedSize(): Long =
    managedEntries().filter(File::isManagedRegularFile).sumOf(File::length)
