package com.ekkus.offlineytplayer.qualification

internal enum class StorageFailureStep {
    PreflightInsufficientSpace,
    RejectDownloadBeforeWrite,
    DetectPartialFile,
    RemovePartialFile,
    PreserveCompletedAssets,
    ShowRecoveryAction,
}

internal data class StorageFailureRecovery(
    val message: String,
    val actionLabel: String,
)

internal object StorageFailureE2EPolicy {
    val requiredFlow = StorageFailureStep.entries.toList()
    val recovery = StorageFailureRecovery(
        message = "Not enough storage space to complete this download.",
        actionLabel = "Manage storage",
    )

    fun accepts(flow: List<StorageFailureStep>): Boolean = flow == requiredFlow
    fun hasClearRecoveryAction(): Boolean = recovery.message.isNotBlank() && recovery.actionLabel.isNotBlank()
}
