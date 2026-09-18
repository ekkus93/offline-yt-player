package com.ekkus.offlineytplayer.resilience

internal enum class RecoveryCondition {
    MissingAsset,
    CorruptDatabase,
    CorruptMedia,
    IncompleteMigration,
    IncompleteTransfer,
}

internal enum class RecoveryAction {
    RemoveStaleReference,
    QuarantineDatabase,
    MarkMediaUnavailable,
    RollBackMigration,
    ResumeOrDiscardTransfer,
}

internal object CorruptionRecoveryPolicy {
    val startupConditions = RecoveryCondition.entries.toSet()

    fun actionFor(condition: RecoveryCondition): RecoveryAction = when (condition) {
        RecoveryCondition.MissingAsset -> RecoveryAction.RemoveStaleReference
        RecoveryCondition.CorruptDatabase -> RecoveryAction.QuarantineDatabase
        RecoveryCondition.CorruptMedia -> RecoveryAction.MarkMediaUnavailable
        RecoveryCondition.IncompleteMigration -> RecoveryAction.RollBackMigration
        RecoveryCondition.IncompleteTransfer -> RecoveryAction.ResumeOrDiscardTransfer
    }

    fun isDestructiveWithoutExplicitRecovery(action: RecoveryAction): Boolean = false
}
