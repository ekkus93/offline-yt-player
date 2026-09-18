package com.ekkus.offlineytplayer.qualification

internal enum class E2EStep {
    ResolveFixture,
    SelectQuality,
    StartDownload,
    KillDuringTransfer,
    Restart,
    Resume,
    CompleteAndVerify,
    DisableNetwork,
    PlayLocalAsset,
}

internal data class OfflineE2EFixture(
    val sourceId: String = "fixture://offline-e2e-v1",
    val quality: String = "720p",
    val deterministic: Boolean = true,
    val requiresPublicNetworkSource: Boolean = false,
)

internal object OfflineE2EFixturePolicy {
    val requiredFlow = E2EStep.entries.toList()
    val fixture = OfflineE2EFixture()

    fun accepts(flow: List<E2EStep>): Boolean =
        fixture.deterministic && !fixture.requiresPublicNetworkSource && flow == requiredFlow
}
