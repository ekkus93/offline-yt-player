package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityQualificationPolicyTest {
    @Test
    fun everyPrimarySurfaceMeetsAccessibilityGate() {
        assertEquals(
            listOf("Library", "Add", "Download Setup", "Downloads", "Player", "Settings"),
            AccessibilityQualificationPolicy.surfaces.map { it.name },
        )
        assertTrue(AccessibilityQualificationPolicy.surfaces.all(AccessibilityQualificationPolicy::qualifies))
    }

    @Test
    fun touchTargetAndTextScaleMatchProductQualification() {
        assertEquals(48, AccessibilityQualificationPolicy.RequiredTouchTargetDp)
        assertEquals(48, MidnightTransit.MinimumTouchTarget.value.toInt())
        assertEquals(1.3f, AccessibilityQualificationPolicy.QualifiedLargeTextScale)
    }

    @Test
    fun missingSemanticCueFailsGate() {
        val invalid = AccessibilityQualificationPolicy.surfaces.first().copy(hasNonColorStatusCue = false)
        assertFalse(AccessibilityQualificationPolicy.qualifies(invalid))
    }

    @Test
    fun undersizedTargetFailsGate() {
        val invalid = AccessibilityQualificationPolicy.surfaces.first().copy(minimumTouchTargetDp = 47)
        assertFalse(AccessibilityQualificationPolicy.qualifies(invalid))
    }
}
