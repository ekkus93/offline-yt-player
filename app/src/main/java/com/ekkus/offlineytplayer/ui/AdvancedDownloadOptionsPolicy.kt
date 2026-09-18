package com.ekkus.offlineytplayer.ui

internal data class AdvancedDownloadOptionsPolicy(
    val subtitleChoiceOnPrimarySetup: Boolean = false,
    val audioChoiceOnPrimarySetup: Boolean = false,
    val advancedChoicesOnPrimarySetup: Boolean = false,
    val subtitleChoiceVisible: Boolean = true,
    val audioChoiceVisible: Boolean = true,
    val advancedChoicesVisible: Boolean = true,
    val primaryControlsScrollable: Boolean = false,
) {
    fun primarySetupIsFocused(): Boolean =
        !subtitleChoiceOnPrimarySetup && !audioChoiceOnPrimarySetup && !advancedChoicesOnPrimarySetup

    fun subpagePrimaryControlsFit(): Boolean =
        subtitleChoiceVisible && audioChoiceVisible && advancedChoicesVisible && !primaryControlsScrollable
}
