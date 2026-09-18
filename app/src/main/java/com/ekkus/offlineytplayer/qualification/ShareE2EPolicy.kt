package com.ekkus.offlineytplayer.qualification

internal enum class ShareE2EStep {
    ReceiveAndroidShareUrl,
    ValidateUrl,
    ResolveMetadata,
    SelectDefaultQuality,
    Download,
    VerifyCompletedLibraryItem,
}

internal object ShareE2EPolicy {
    val requiredFlow = ShareE2EStep.entries.toList()

    fun accepts(flow: List<ShareE2EStep>): Boolean = flow == requiredFlow

    fun acceptsSharedText(text: String?): Boolean {
        val value = text?.trim().orEmpty()
        return value.startsWith("https://") && value.length <= 2048
    }
}
