package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddScreenPolicyTest {
    @Test
    fun primaryAddUiIsCompleteAndNonScrolling() {
        val policy = AddScreenPolicy()
        assertTrue(policy.urlFieldVisible)
        assertTrue(policy.pasteActionVisible)
        assertTrue(policy.analyzeActionVisible)
        assertTrue(policy.supportedSourceHintVisible)
        assertFalse(policy.primaryScreenScrollable)
        assertTrue(policy.primaryControlsFit())
    }

    @Test
    fun analyzeRequiresHttpUrlCandidate() {
        val policy = AddScreenPolicy()
        assertTrue(policy.canAnalyze("https://www.youtube.com/watch?v=fixture"))
        assertTrue(policy.canAnalyze(" http://example.test/video "))
        assertFalse(policy.canAnalyze("not a url"))
        assertFalse(policy.canAnalyze("file:///tmp/video.mp4"))
    }
}
