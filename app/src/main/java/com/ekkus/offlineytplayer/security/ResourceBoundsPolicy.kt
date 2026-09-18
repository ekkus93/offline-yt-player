package com.ekkus.offlineytplayer.security

internal object ResourceBoundsPolicy {
    const val ConnectTimeoutSeconds = 15
    const val ReadTimeoutSeconds = 30
    const val MaximumMetadataBytes = 2 * 1024 * 1024
    const val MaximumConcurrentDownloads = 3
    const val MinimumFreeSpaceReserveBytes = 128L * 1024L * 1024L

    fun bodySizeAllowed(bytes: Long): Boolean = bytes in 0..MaximumMetadataBytes.toLong()

    fun concurrencyAllowed(activeDownloads: Int): Boolean = activeDownloads in 0..MaximumConcurrentDownloads

    fun hasDiskCapacity(freeBytes: Long, expectedBytes: Long): Boolean {
        if (freeBytes < 0 || expectedBytes < 0) return false
        return freeBytes >= expectedBytes + MinimumFreeSpaceReserveBytes
    }
}
