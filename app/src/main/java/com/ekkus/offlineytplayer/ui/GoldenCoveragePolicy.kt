package com.ekkus.offlineytplayer.ui

internal enum class GoldenProfile { CompactPortrait, LargePortrait }

internal enum class GoldenSurface {
    Library,
    Add,
    DownloadSetup,
    Downloads,
    Player,
    SettingsHub,
    SettingsDownloads,
    SettingsPlayback,
    SettingsStorage,
    SettingsAppearance,
    SettingsAbout,
}

internal data class GoldenScenario(
    val surface: GoldenSurface,
    val profile: GoldenProfile,
)

internal object GoldenCoveragePolicy {
    val Profiles = listOf(GoldenProfile.CompactPortrait, GoldenProfile.LargePortrait)
    val Surfaces = GoldenSurface.entries
    val RequiredScenarios = Surfaces.flatMap { surface ->
        Profiles.map { profile -> GoldenScenario(surface, profile) }
    }

    // This is a target-scenario inventory, NOT executable screenshot evidence.
    // Only ProductionComposeGoldenTest on an emulator can qualify bitmap captures.

}
