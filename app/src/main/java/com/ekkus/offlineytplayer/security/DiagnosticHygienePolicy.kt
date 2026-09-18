package com.ekkus.offlineytplayer.security

internal object DiagnosticHygienePolicy {
    val ForbiddenFieldNames = setOf("authorization", "cookie", "set-cookie", "credential", "signature", "token")

    fun mayExportField(name: String): Boolean = name.lowercase() !in ForbiddenFieldNames

    fun safeFields(fields: Map<String, String>): Map<String, String> =
        fields.filterKeys(::mayExportField)
}
