package com.ekkus.offlineytplayer.ui

internal data class StorageSummary(
    val locationLabel: String,
    val usedBytes: Long,
    val freeBytes: Long,
    val cacheBytes: Long,
    val orphanBytes: Long,
    val incompleteBytes: Long,
) {
    init {
        require(locationLabel.isNotBlank()) { "storage location summary must be visible" }
        require(listOf(usedBytes, freeBytes, cacheBytes, orphanBytes, incompleteBytes).all { it >= 0 }) {
            "storage byte counts cannot be negative"
        }
    }
}

internal enum class StorageCleanupAction { ClearCache, RemoveOrphans, RemoveIncomplete }

internal object StorageSettingsPolicy {
    val CleanupActions = listOf(
        StorageCleanupAction.ClearCache,
        StorageCleanupAction.RemoveOrphans,
        StorageCleanupAction.RemoveIncomplete,
    )

    fun reclaimableBytes(summary: StorageSummary): Long =
        summary.cacheBytes + summary.orphanBytes + summary.incompleteBytes
}
