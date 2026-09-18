package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutPolicyTest {
    private val info = AboutInfo(
        versionName = "1.2.3",
        versionCode = 42,
        sourceRevision = "0123456789abcdef",
    )

    @Test
    fun aboutSurfaceContainsRequiredBoundedRows() {
        val rows = AboutPolicy.primaryRows(info)
        assertEquals(AboutPolicy.MaximumPrimaryRows, rows.size)
        assertTrue(rows.first().contains("1.2.3"))
        assertTrue(rows.any { it.contains("licenses", ignoreCase = true) })
        assertTrue(rows.any { it == "Privacy" })
        assertTrue(rows.any { it == "Legal" })
        assertTrue(rows.last().contains("0123456789ab"))
    }

    @Test
    fun privacyAndLegalCopyAreExplicit() {
        assertTrue(info.privacySummary.contains("device"))
        assertTrue(info.legalNotice.contains("third parties"))
        assertTrue(AboutPolicy.DiagnosticsExportOptional)
    }
}
