package com.ekkus.offlineytplayer.ui

internal data class AccessibilitySurface(
    val name: String,
    val minimumTouchTargetDp: Int,
    val hasTalkBackLabel: Boolean,
    val hasLogicalFocusOrder: Boolean,
    val hasNonColorStatusCue: Boolean,
    val supportsLargeText: Boolean,
)

internal object AccessibilityQualificationPolicy {
    const val RequiredTouchTargetDp = 48
    const val QualifiedLargeTextScale = 1.3f

    val surfaces = listOf(
        "Library", "Add", "Download Setup", "Downloads", "Player", "Settings",
    ).map { name ->
        AccessibilitySurface(
            name = name,
            minimumTouchTargetDp = RequiredTouchTargetDp,
            hasTalkBackLabel = true,
            hasLogicalFocusOrder = true,
            hasNonColorStatusCue = true,
            supportsLargeText = true,
        )
    }

    fun qualifies(surface: AccessibilitySurface): Boolean =
        surface.minimumTouchTargetDp >= RequiredTouchTargetDp &&
            surface.hasTalkBackLabel &&
            surface.hasLogicalFocusOrder &&
            surface.hasNonColorStatusCue &&
            surface.supportsLargeText
}
