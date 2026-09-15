package com.ekkus.offlineytplayer

import java.net.URI

internal object ShareInput {
    private const val SendAction = "android.intent.action.SEND"
    private const val PlainText = "text/plain"
    private const val MaxSharedTextLength = 8_192

    fun parse(action: String?, mimeType: String?, sharedText: String?): String? {
        if (action != SendAction || mimeType != PlainText) return null
        val text = sharedText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.length > MaxSharedTextLength) return null
        val candidate = text.split(Regex("\\s+")).firstOrNull { token ->
            token.startsWith("https://") || token.startsWith("http://")
        } ?: return null
        return runCatching { URI(candidate) }.getOrNull()?.let { uri ->
            if ((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) {
                uri.toASCIIString()
            } else {
                null
            }
        }
    }
}
