package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.coregateway.SourceMetadataPolicy

internal data class ClipboardPasteDecision(
    val acceptedText: String?,
    val statusMessage: String,
)

internal object AddWorkflowPolicy {
    const val MaxClipboardChars: Int = 4096
    const val MissingClipboardMessage: String = "Clipboard does not contain a video URL."
    const val PastedClipboardMessage: String = "Pasted clipboard text. Choose Analyze to validate it."

    fun boundedClipboardText(rawText: CharSequence?): ClipboardPasteDecision {
        val source = rawText ?: return ClipboardPasteDecision(null, MissingClipboardMessage)
        // Clipboard text is untrusted and may be arbitrarily large. Bound reads and
        // allocations before materializing it; never call toString on the full source.
        val limit = source.length.coerceAtMost(MaxClipboardChars)
        val bounded = buildString(limit) {
            for (index in 0 until limit) append(source[index])
        }
        if (bounded.isBlank()) return ClipboardPasteDecision(null, MissingClipboardMessage)
        return ClipboardPasteDecision(bounded, PastedClipboardMessage)
    }

    fun qualityLabels(resolvedLabels: List<String>, fallbackLabel: String): List<String> =
        resolvedLabels
            // The original label is the key in the production format-choice map.
            // Trimming it here would silently disconnect the UI from its choice ID.
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(fallbackLabel).filter { it.isNotBlank() } }

    fun preferredQualityLabel(options: List<String>, configured: String, fallback: String): String =
        options.firstOrNull { it.trim().equals(configured.trim(), ignoreCase = true) }
            ?: options.firstOrNull { it.trim().equals(fallback.trim(), ignoreCase = true) }
            ?: options.firstOrNull()
            ?: configured.takeIf { it.isNotBlank() }
            ?: fallback

    fun optionSummary(options: List<String>, fallback: String): String =
        options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: fallback

    /**
     * Sanitize only UI presentation: resolved provider labels are still kept verbatim
     * as selection-map keys so that multiple provider formats remain selectable.
     */
    fun displayLabel(value: String): String =
        SourceMetadataPolicy.qualityLabel(SourceMetadataPolicy.diagnostic(value))

    fun displaySummary(options: List<String>, fallback: String): String {
        val shown = options.take(8).map(::displayLabel)
        val more = if (options.size > 8) " · … ${options.size - 8} more" else ""
        return (optionSummary(shown, displayLabel(fallback)) + more)
            .take(SourceMetadataPolicy.MaxDiagnosticChars)
    }

    fun downloadSettingsSummary(wifiOnlyDownloads: Boolean, maxConcurrentDownloads: Int): String =
        "${if (wifiOnlyDownloads) "Wi-Fi only" else "Any network"} · $maxConcurrentDownloads concurrent"
}
