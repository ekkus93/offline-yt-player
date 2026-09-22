package com.ekkus.offlineytplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Runtime-backed About content kept separate from the settings shell for testability. */
@Composable
internal fun AboutSettingsPage() {
    val metadata = AboutMetadataProvider.current()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MidnightTransit.SectionSpacing),
    ) {
        AboutSettingValue("Version / build", AboutMetadataProvider.versionLabel(metadata))
        AboutSettingValue("Source revision", AboutMetadataProvider.revisionLabel(metadata))
        AboutSettingValue("Licenses", metadata.licenses)
        AboutSettingValue("Privacy", metadata.privacy)
        AboutSettingValue("Diagnostics", metadata.diagnostics)
        AboutSettingValue("Support", metadata.support)
    }
}
