package com.ekkus.offlineytplayer.resilience

internal enum class RecoveryCondition {
    MissingAsset,
    CorruptDatabase,
    CorruptMedia,
    UnsupportedSchema,
    IncompleteMigration,
    IncompleteTransfer,
}

internal enum class RecoveryAction {
    RemoveStaleReference,
    QuarantineDatabase,
    MarkMediaUnavailable,
    UpgradeApplication,
    RollBackMigration,
    ResumeOrDiscardTransfer,
}

internal enum class RecoveryUiAction {
    RetryStartup,
    ExportDiagnostics,
    OpenSupport,
    ResetLocalData,
    UpgradeApplication,
}

internal data class RecoveryUiState(
    val condition: RecoveryCondition,
    val title: String,
    val message: String,
    val actions: List<RecoveryUiAction>,
)

internal object CorruptionRecoveryPolicy {
    val startupConditions = RecoveryCondition.entries.toSet()

    fun actionFor(condition: RecoveryCondition): RecoveryAction = when (condition) {
        RecoveryCondition.MissingAsset -> RecoveryAction.RemoveStaleReference
        RecoveryCondition.CorruptDatabase -> RecoveryAction.QuarantineDatabase
        RecoveryCondition.CorruptMedia -> RecoveryAction.MarkMediaUnavailable
        RecoveryCondition.UnsupportedSchema -> RecoveryAction.UpgradeApplication
        RecoveryCondition.IncompleteMigration -> RecoveryAction.RollBackMigration
        RecoveryCondition.IncompleteTransfer -> RecoveryAction.ResumeOrDiscardTransfer
    }

    fun isDestructiveWithoutExplicitRecovery(action: RecoveryAction): Boolean = false

    fun classify(errorKind: String, message: String): RecoveryCondition? {
        val kind = errorKind.lowercase()
        val detail = message.lowercase()
        return when {
            "newer than this application" in detail || "unsupported schema" in detail -> RecoveryCondition.UnsupportedSchema
            "database" in detail && ("corrupt" in detail || "malformed" in detail || "persistence" in kind) -> RecoveryCondition.CorruptDatabase
            "missing" in kind || "missing asset" in detail || "missing media" in detail -> RecoveryCondition.MissingAsset
            "corrupt" in kind || "integrity" in kind || "hash" in detail || "size" in detail -> RecoveryCondition.CorruptMedia
            "migration" in detail -> RecoveryCondition.IncompleteMigration
            "transfer" in detail || "download" in detail -> RecoveryCondition.IncompleteTransfer
            else -> null
        }
    }

    fun uiState(errorKind: String, message: String): RecoveryUiState? = classify(errorKind, message)?.let(::uiState)

    fun uiState(condition: RecoveryCondition): RecoveryUiState = when (condition) {
        RecoveryCondition.MissingAsset -> RecoveryUiState(condition, "Offline media is missing", "The library record is intact, but one or more local media files are missing. You can retry startup recovery or export diagnostics before changing local data.", safeRecoveryActions())
        RecoveryCondition.CorruptMedia -> RecoveryUiState(condition, "Offline media failed integrity checks", "A local file does not match its recorded size or hash. Playback is disabled for the affected media until it is repaired or downloaded again.", safeRecoveryActions())
        RecoveryCondition.UnsupportedSchema -> RecoveryUiState(condition, "Library was created by a newer app version", "This app cannot safely open the current library database. Upgrade the app; the database will not be reset automatically.", listOf(RecoveryUiAction.UpgradeApplication, RecoveryUiAction.ExportDiagnostics, RecoveryUiAction.OpenSupport))
        RecoveryCondition.CorruptDatabase -> RecoveryUiState(condition, "Library database needs recovery", "The local database could not be opened safely. Export diagnostics or contact support before choosing an explicit local-data reset.", listOf(RecoveryUiAction.ExportDiagnostics, RecoveryUiAction.OpenSupport, RecoveryUiAction.ResetLocalData))
        RecoveryCondition.IncompleteMigration -> RecoveryUiState(condition, "Library migration was interrupted", "Startup recovery could not finish the database migration. Retry recovery or export diagnostics; local data will not be discarded automatically.", safeRecoveryActions())
        RecoveryCondition.IncompleteTransfer -> RecoveryUiState(condition, "Download recovery is incomplete", "Interrupted download state is still durable. Retry startup recovery or manage the affected download when scheduling is legal.", safeRecoveryActions())
    }

    private fun safeRecoveryActions() = listOf(RecoveryUiAction.RetryStartup, RecoveryUiAction.ExportDiagnostics, RecoveryUiAction.OpenSupport)
}
