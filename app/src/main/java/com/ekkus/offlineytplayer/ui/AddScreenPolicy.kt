package com.ekkus.offlineytplayer.ui

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

    fun canAnalyze(url: String): Boolean {
        val value = url.trim()
        return value.startsWith("https://") || value.startsWith("http://")
    }
}
