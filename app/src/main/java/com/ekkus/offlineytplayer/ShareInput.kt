package com.ekkus.offlineytplayer

internal object ShareInput {
    private const val SendAction = "android.intent.action.SEND"
    private const val PlainText = "text/plain"
    private const val MaxSharedTextLength = 8_192

    fun parse(action: String?, mimeType: String?, sharedText: String?): String? {
        if (action != SendAction || mimeType != PlainText) return null
        val text = sharedText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.length > MaxSharedTextLength) return null
        return SupportedUrlPolicy.firstSupportedUrlFromText(text)
    }
}
