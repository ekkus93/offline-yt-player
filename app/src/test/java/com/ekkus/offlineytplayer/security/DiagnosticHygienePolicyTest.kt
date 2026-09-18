package com.ekkus.offlineytplayer.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticHygienePolicyTest {
    @Test
    fun sensitiveNamedFieldsAreExcludedFromDiagnosticExports() {
        val input = mapOf(
            "status" to "failed",
            "authorization" to "sample",
            "cookie" to "sample",
            "signature" to "sample",
            "token" to "sample",
        )
        val output = DiagnosticHygienePolicy.safeFields(input)
        assertEquals(mapOf("status" to "failed"), output)
        assertFalse(output.keys.any { it in DiagnosticHygienePolicy.ForbiddenFieldNames })
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertFalse(DiagnosticHygienePolicy.mayExportField("Authorization"))
        assertTrue(DiagnosticHygienePolicy.mayExportField("durationMs"))
    }
}
