package com.ekkus.offlineytplayer

import java.net.URI

/**
 * Android-side mirror of the production core source-recognition contract.
 *
 * Keep this fail-closed: Share/Add inputs may only enter the pipeline when the
 * portable core production registry can recognize the same URL form. The core
 * remains authoritative for network resolution and canonical source identity;
 * this policy prevents unsupported schemes, hosts, oversized inputs, and
 * non-video YouTube pages from reaching preview/setup UI as if they were valid.
 */
internal object SupportedUrlPolicy {
    const val MaxUrlLength = 4_096

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
        return if (isValidVideoId(videoId)) uri.toASCIIString() else null
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
        return uri.rawQuery
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
