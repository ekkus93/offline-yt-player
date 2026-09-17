package com.ekkus.offlineytplayer.ui

internal data class CompactMetadata(
    val title: String,
    val duration: String?,
    val quality: String?,
    val source: String?,
)

internal data class MetadataDetailRow(
    val label: String,
    val value: String,
)

internal object MetadataPresentationPolicy {
    const val PrimaryTitleMaxLines = 2
    const val PrimaryMetadataFieldCount = 3
    const val PrimaryScreenUsesUnboundedMetadata = false
    const val LongDetailsUseDedicatedRegion = true

    fun compact(
        title: String,
        duration: String?,
        quality: String?,
        source: String?,
    ): CompactMetadata = CompactMetadata(
        title = title.trim(),
        duration = duration?.trim()?.takeIf { it.isNotEmpty() },
        quality = quality?.trim()?.takeIf { it.isNotEmpty() },
        source = source?.trim()?.takeIf { it.isNotEmpty() },
    )

    fun details(fields: Map<String, String>): List<MetadataDetailRow> = fields
        .asSequence()
        .map { (label, value) -> label.trim() to value.trim() }
        .filter { (label, value) -> label.isNotEmpty() && value.isNotEmpty() }
        .sortedBy { (label, _) -> label.lowercase() }
        .map { (label, value) -> MetadataDetailRow(label, value) }
        .toList()
}
