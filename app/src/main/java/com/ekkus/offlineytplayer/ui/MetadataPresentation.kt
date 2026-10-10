package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.SourceMetadataPolicy

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

    fun compact(
        title: String,
        duration: String?,
        quality: String?,
        source: String?,
    ): CompactMetadata = CompactMetadata(
        title = SourceMetadataPolicy.title(title),
        duration = duration?.trim()?.takeIf { it.isNotEmpty() },
        quality = quality?.let(SourceMetadataPolicy::qualityLabel),
        source = source?.trim()?.takeIf { it.isNotEmpty() }?.take(SourceMetadataPolicy.MaxChannelChars),
    )

    fun details(fields: Map<String, String>): List<MetadataDetailRow> = fields
        .asSequence()
        .map { (label, value) -> label.trim().take(64) to value.trim().take(SourceMetadataPolicy.MaxDescriptionChars) }
        .filter { (label, value) -> label.isNotEmpty() && value.isNotEmpty() }
        .sortedBy { (label, _) -> label.lowercase() }
        .map { (label, value) -> MetadataDetailRow(label, value) }
        .toList()
}
