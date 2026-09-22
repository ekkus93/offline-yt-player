package com.ekkus.offlineytplayer.coregateway

/** Defense-in-depth sanitizer for diagnostics crossing the Rust/Android boundary. */
internal object DiagnosticRedaction {
    private const val MAX_MESSAGE_CHARS = 512
    private val url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val sensitiveAssignment = Regex(
        "(?i)(authorization|cookie|set-cookie|x-api-key|token|access_token|refresh_token|signature|sig|key)=([^&\\s]+)",
    )

    fun sanitize(message: String): String {
        val withoutAssignments = sensitiveAssignment.replace(message) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        val withoutUrlQueries = url.replace(withoutAssignments) { match ->
            val raw = match.value
            val query = raw.indexOf('?')
            if (query < 0) raw else raw.substring(0, query) + "?[REDACTED]"
        }
        return withoutUrlQueries
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(MAX_MESSAGE_CHARS)
    }
}
