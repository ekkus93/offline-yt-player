package com.ekkus.offlineytplayer.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorruptionRecoveryPolicyTest {
    @Test
    fun startupReconciliationCoversRequiredFailureClasses() {
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.MissingAsset))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.CorruptDatabase))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.CorruptMedia))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.IncompleteMigration))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.IncompleteTransfer))
    }

    @Test
    fun eachFailureClassHasDeterministicRecovery() {
        assertEquals(RecoveryAction.RemoveStaleReference, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.MissingAsset))
        assertEquals(RecoveryAction.QuarantineDatabase, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.CorruptDatabase))
        assertEquals(RecoveryAction.MarkMediaUnavailable, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.CorruptMedia))
        assertEquals(RecoveryAction.RollBackMigration, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.IncompleteMigration))
        assertEquals(RecoveryAction.ResumeOrDiscardTransfer, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.IncompleteTransfer))
    }

    @Test
    fun recoveryPolicyDoesNotSilentlyDestroyUserData() {
        RecoveryAction.entries.forEach { action ->
            assertFalse(CorruptionRecoveryPolicy.isDestructiveWithoutExplicitRecovery(action))
        }
    }
}
