package com.ekkus.offlineytplayer.downloads

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionInstrumentationContractTest {
    @Test
    fun deviceCoverageExercisesGrantedAndDeniedPersistence() {
        val test = File(
            "src/androidTest/java/com/ekkus/offlineytplayer/downloads/NotificationPermissionStateInstrumentationTest.kt",
        )
        assertTrue("notification permission instrumentation test must exist", test.isFile)
        val source = test.readText()
        assertTrue(source.contains("recordGrantState(context, true)"))
        assertTrue(source.contains("recordGrantState(context, false)"))
        assertTrue(source.contains("recordedGrantState(context)"))
    }
}
