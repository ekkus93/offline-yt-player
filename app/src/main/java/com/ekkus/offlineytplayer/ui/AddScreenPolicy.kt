package com.ekkus.offlineytplayer.ui

import com.ekkus.offlineytplayer.SupportedUrlPolicy

internal data class AddScreenPolicy(
    val urlFieldVisible: Boolean = true,
    val pasteActionVisible: Boolean = true,
    val analyzeActionVisible: Boolean = true,
    val supportedSourceHintVisible: Boolean = true,
    val primaryScreenScrollable: Boolean = false,
) {
    fun primaryControlsFit(): Boolean =
        urlFieldVisible && pasteActionVisible && analyzeActionVisible &&
            supportedSourceHintVisible && !primaryScreenScrollable

    fun normalizedAnalyzeUrl(url: String): String? = SupportedUrlPolicy.normalizeSupportedUrl(url)

    fun canAnalyze(url: String): Boolean = normalizedAnalyzeUrl(url) != null
}
