package com.ekkus.offlineytplayer

import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapTest {
    @Test
    fun applicationIdIsStable() {
        assertTrue(BuildConfig.APPLICATION_ID.endsWith("offlineytplayer"))
    }
}
