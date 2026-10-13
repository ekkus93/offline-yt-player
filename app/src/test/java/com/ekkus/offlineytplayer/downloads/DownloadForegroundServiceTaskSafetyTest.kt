package com.ekkus.offlineytplayer.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadForegroundServiceTaskSafetyTest {
    @Test
    fun gatewayFailureCannotEscapeTheForegroundCallbackThreadBoundary() {
        var failureReports = 0
        val completed = runForegroundServiceTaskSafely(
            action = { throw IllegalStateException("private signed URL and DB path") },
            onFailure = { failureReports++ },
        )
        assertFalse(completed)
        assertEquals(1, failureReports)
    }

    @Test
    fun successfulCallbackDoesNotReportAnUnexpectedFailure() {
        var executions = 0
        var failureReports = 0
        val completed = runForegroundServiceTaskSafely(
            action = { executions++ },
            onFailure = { failureReports++ },
        )
        assertTrue(completed)
        assertEquals(1, executions)
        assertEquals(0, failureReports)
    }
}
