package com.ekkus.offlineytplayer

import java.net.URI

/**
 * Android-side entry point for the same production source-recognition contract
 * enforced by the Rust core's YouTube recognizer.
 *
 * The canonical form returned here intentionally matches the core recognizer:
 * `https://www.youtube.com/watch?v=<11-character-id>`. Share/Add inputs may
 * only enter the production analysis pipeline after this fail-closed check, so
 * unsupported schemes, hosts, oversized inputs, credentials, and non-video
 * YouTube pages are rejected before they can be presented as resolvable media.
 */
internal object SupportedUrlPolicy {
    const val MaxUrlLength = 4_096

    private const val CanonicalYouTubePrefix = "https://www.youtube.com/watch?v="

    private val YouTubeWatchHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
    )

    fun normalizeSupportedUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MaxUrlLength) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val videoId = when {
            host == "youtu.be" -> shortVideoId(uri)
            host in YouTubeWatchHosts -> watchVideoId(uri)
            else -> null
        } ?: return null
        return if (isValidVideoId(videoId)) "$CanonicalYouTubePrefix$videoId" else null
    }

    fun supportedUrlsFromText(text: String): List<String> =
        text.trim()
            .split(Regex("\\s+"))
            .mapNotNull { token -> normalizeSupportedUrl(token.trimSharePunctuation()) }
            .distinct()

    fun firstSupportedUrlFromText(text: String): String? = supportedUrlsFromText(text).firstOrNull()

    fun singleSupportedUrlFromText(text: String): String? = supportedUrlsFromText(text).singleOrNull()

    private fun shortVideoId(uri: URI): String? {
        val segments = uri.path
            ?.split('/')
            ?.filter { it.isNotEmpty() }
            ?: return null
        return segments.singleOrNull()
    }

    private fun watchVideoId(uri: URI): String? {
        if (uri.path != "/watch") return null
        return uri.query
            ?.split('&')
            ?.firstNotNullOfOrNull { pair ->
                val key = pair.substringBefore('=', missingDelimiterValue = pair)
                val value = pair.substringAfter('=', missingDelimiterValue = "")
                if (key == "v" && value.isNotBlank()) value else null
            }
    }

    private fun isValidVideoId(id: String): Boolean =
        id.length == 11 && id.all { char -> char.isLetterOrDigit() || char == '-' || char == '_' }

    private fun String.trimSharePunctuation(): String =
        trim { char -> char in setOf('<', '>', '"', '\'', '(', ')', '[', ']', '{', '}', ',', '.') }
}
