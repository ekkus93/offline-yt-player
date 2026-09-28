package com.ekkus.offlineytplayer.ui

internal data class ClipboardPasteDecision(
    val acceptedText: String?,
    val statusMessage: String,
)

internal object AddWorkflowPolicy {
    const val MaxClipboardChars: Int = 4096
    const val MissingClipboardMessage: String = "Clipboard does not contain a video URL."
    const val PastedClipboardMessage: String = "Pasted clipboard text. Choose Analyze to validate it."

    fun boundedClipboardText(rawText: CharSequence?): ClipboardPasteDecision {
        val value = rawText?.toString()?.takeIf { it.isNotBlank() }
            ?: return ClipboardPasteDecision(null, MissingClipboardMessage)
        return ClipboardPasteDecision(value.take(MaxClipboardChars), PastedClipboardMessage)
    }

    fun qualityLabels(resolvedLabels: List<String>, fallbackLabel: String): List<String> =
        resolvedLabels
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(fallbackLabel).filter { it.isNotBlank() } }

    fun preferredQualityLabel(options: List<String>, configured: String, fallback: String): String =
        options.firstOrNull { it.equals(configured, ignoreCase = true) }
            ?: configured.takeIf { it.isNotBlank() && options.isEmpty() }
            ?: fallback

    fun optionSummary(options: List<String>, fallback: String): String =
        options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: fallback

    fun downloadSettingsSummary(wifiOnlyDownloads: Boolean, maxConcurrentDownloads: Int): String =
        "${if (wifiOnlyDownloads) "Wi-Fi only" else "Any network"} · $maxConcurrentDownloads concurrent"
}
