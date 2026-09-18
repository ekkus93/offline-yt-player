package com.ekkus.offlineytplayer.ui

internal enum class PrimaryControlSurface {
    Library,
    Add,
    DownloadSetup,
    Downloads,
    Player,
    Settings,
}

internal data class PrimaryControlBudget(
    val surface: PrimaryControlSurface,
    val primaryActionCount: Int,
    val requiresVerticalScrollForPrimaryActions: Boolean,
    val hasHorizontalControlScrolling: Boolean,
    val exposesLandscapeOnlyAffordance: Boolean,
)

internal object NoHiddenControlsPolicy {
    val Budgets = listOf(
        PrimaryControlBudget(PrimaryControlSurface.Library, primaryActionCount = 3, false, false, false),
        PrimaryControlBudget(PrimaryControlSurface.Add, primaryActionCount = 4, false, false, false),
        PrimaryControlBudget(PrimaryControlSurface.DownloadSetup, primaryActionCount = 4, false, false, false),
        PrimaryControlBudget(PrimaryControlSurface.Downloads, primaryActionCount = 4, false, false, false),
        PrimaryControlBudget(PrimaryControlSurface.Player, primaryActionCount = 6, false, false, false),
        PrimaryControlBudget(PrimaryControlSurface.Settings, primaryActionCount = 5, false, false, false),
    )

    const val TargetProfilesIncludeCompactPortrait = true
    const val TargetProfilesIncludeLargePortrait = true

    fun primaryActionsVisibleWithoutScrolling(surface: PrimaryControlSurface): Boolean =
        budget(surface).primaryActionCount > 0 && !budget(surface).requiresVerticalScrollForPrimaryActions

    fun noHorizontalControlScrolling(surface: PrimaryControlSurface): Boolean =
        !budget(surface).hasHorizontalControlScrolling

    fun noLandscapeOnlyAffordance(surface: PrimaryControlSurface): Boolean =
        !budget(surface).exposesLandscapeOnlyAffordance

    private fun budget(surface: PrimaryControlSurface): PrimaryControlBudget =
        Budgets.single { it.surface == surface }
}
