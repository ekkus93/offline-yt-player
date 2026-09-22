package com.ekkus.offlineytplayer.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorruptionRecoveryPolicyTest {
    @Test fun startupReconciliationCoversRequiredFailureClasses() {
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.MissingAsset))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.CorruptDatabase))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.CorruptMedia))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.UnsupportedSchema))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.IncompleteMigration))
        assertTrue(CorruptionRecoveryPolicy.startupConditions.contains(RecoveryCondition.IncompleteTransfer))
    }

    @Test fun eachFailureClassHasDeterministicRecovery() {
        assertEquals(RecoveryAction.RemoveStaleReference, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.MissingAsset))
        assertEquals(RecoveryAction.QuarantineDatabase, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.CorruptDatabase))
        assertEquals(RecoveryAction.MarkMediaUnavailable, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.CorruptMedia))
        assertEquals(RecoveryAction.UpgradeApplication, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.UnsupportedSchema))
        assertEquals(RecoveryAction.RollBackMigration, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.IncompleteMigration))
        assertEquals(RecoveryAction.ResumeOrDiscardTransfer, CorruptionRecoveryPolicy.actionFor(RecoveryCondition.IncompleteTransfer))
    }

    @Test fun recoveryPolicyDoesNotSilentlyDestroyUserData() {
        RecoveryAction.entries.forEach { assertFalse(CorruptionRecoveryPolicy.isDestructiveWithoutExplicitRecovery(it)) }
    }

    @Test fun realCoreFailureShapesBecomeActionableUiState() {
        assertEquals(RecoveryCondition.UnsupportedSchema, CorruptionRecoveryPolicy.classify("Persistence", "library database is newer than this application"))
        assertEquals(RecoveryCondition.CorruptMedia, CorruptionRecoveryPolicy.classify("IntegrityFailure", "asset hash mismatch"))
        assertEquals(RecoveryCondition.MissingAsset, CorruptionRecoveryPolicy.classify("MissingAsset", "media file is missing"))
        assertEquals(RecoveryCondition.CorruptDatabase, CorruptionRecoveryPolicy.classify("Persistence", "database disk image is malformed"))
        RecoveryCondition.entries.forEach { condition ->
            val state = CorruptionRecoveryPolicy.uiState(condition)
            assertTrue(state.title.isNotBlank())
            assertTrue(state.message.isNotBlank())
            assertTrue(state.actions.isNotEmpty())
        }
    }

    @Test fun destructiveResetIsOnlyOfferedForDamagedDatabaseAndNeverAutomatic() {
        assertTrue(CorruptionRecoveryPolicy.uiState(RecoveryCondition.CorruptDatabase).actions.contains(RecoveryUiAction.ResetLocalData))
        assertFalse(CorruptionRecoveryPolicy.uiState(RecoveryCondition.MissingAsset).actions.contains(RecoveryUiAction.ResetLocalData))
        assertFalse(CorruptionRecoveryPolicy.uiState(RecoveryCondition.CorruptMedia).actions.contains(RecoveryUiAction.ResetLocalData))
        assertFalse(CorruptionRecoveryPolicy.uiState(RecoveryCondition.UnsupportedSchema).actions.contains(RecoveryUiAction.ResetLocalData))
        assertNotNull(CorruptionRecoveryPolicy.uiState("Persistence", "database is corrupt"))
    }
}
