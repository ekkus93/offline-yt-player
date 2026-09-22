package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactionTest {
    @Test
    fun stripsSyntheticSignedUrlAndTokenValues() {
        val marker = "SYNTHETIC_SECRET_MARKER_1302"
        val message = "request https://cdn.example/video?signature=$marker&token=$marker failed token=$marker"
        val sanitized = DiagnosticRedaction.sanitize(message)

        assertFalse(sanitized.contains(marker))
        assertFalse(sanitized.contains("signature=$marker"))
        assertFalse(sanitized.contains("token=$marker"))
        assertTrue(sanitized.contains("?[REDACTED]"))
        assertTrue(sanitized.contains("token=[REDACTED]"))
    }

    @Test
    fun boundsAndFlattensUserVisibleDiagnostics() {
        val sanitized = DiagnosticRedaction.sanitize("first\nsecond\t" + "x".repeat(2_000))
        assertFalse(sanitized.contains('\n'))
        assertFalse(sanitized.contains('\t'))
        assertTrue(sanitized.length <= 512)
    }
}
