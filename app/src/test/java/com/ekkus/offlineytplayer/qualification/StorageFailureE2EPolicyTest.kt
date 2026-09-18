package com.ekkus.offlineytplayer.qualification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFailureE2EPolicyTest {
    @Test
    fun flowCoversSpacePreflightPartialCleanupAndRecovery() {
        assertTrue(StorageFailureE2EPolicy.accepts(StorageFailureStep.entries.toList()))
        assertTrue(StorageFailureE2EPolicy.hasClearRecoveryAction())
    }

    @Test
    fun cleanupMustPreserveCompletedAssets() {
        assertFalse(StorageFailureE2EPolicy.accepts(StorageFailureStep.entries.filterNot { it == StorageFailureStep.PreserveCompletedAssets }))
    }

    @Test
    fun partialFileCleanupAndUserRecoveryCannotBeOmitted() {
        assertFalse(StorageFailureE2EPolicy.accepts(StorageFailureStep.entries.filterNot { it == StorageFailureStep.RemovePartialFile }))
        assertFalse(StorageFailureE2EPolicy.accepts(StorageFailureStep.entries.filterNot { it == StorageFailureStep.ShowRecoveryAction }))
    }
}
