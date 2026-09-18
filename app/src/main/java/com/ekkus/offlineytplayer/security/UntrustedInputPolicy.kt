package com.ekkus.offlineytplayer.security

import java.net.URI

internal object UntrustedInputPolicy {
    private val unsafeFilenameChars = Regex("[^A-Za-z0-9._ -]")
    private val controlChars = Regex("[\\u0000-\\u001F\\u007F]")

    fun validateSourceUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)

    fun sanitizeMetadata(value: String, maxLength: Int = 512): String =
        controlChars.replace(value, " ").trim().take(maxLength)

    fun sanitizeFilename(value: String): String {
        val leaf = value.replace('\\', '/').substringAfterLast('/')
        val cleaned = unsafeFilenameChars.replace(controlChars.replace(leaf, ""), "_")
            .trim().trim('.').take(180)
        return cleaned.ifBlank { "download" }
    }

    fun isSafeRelativePath(value: String): Boolean {
        if (value.isBlank() || value.startsWith('/') || value.startsWith('\\')) return false
        val normalized = value.replace('\\', '/')
        return normalized.split('/').none { it == ".." || it.isBlank() }
    }
}
