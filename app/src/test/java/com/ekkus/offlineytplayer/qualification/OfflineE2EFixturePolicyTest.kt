package com.ekkus.offlineytplayer.qualification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineE2EFixturePolicyTest {
    @Test
    fun fixtureFlowCoversResolveDownloadRestartResumeAndOfflinePlayback() {
        assertTrue(OfflineE2EFixturePolicy.accepts(E2EStep.entries.toList()))
    }

    @Test
    fun fixtureIsDeterministicAndDoesNotDependOnPublicSourceAvailability() {
        val fixture = OfflineE2EFixturePolicy.fixture
        assertTrue(fixture.deterministic)
        assertFalse(fixture.requiresPublicNetworkSource)
        assertTrue(fixture.sourceId.startsWith("fixture://"))
    }

    @Test
    fun missingRestartOrOfflineStepFailsQualification() {
        assertFalse(OfflineE2EFixturePolicy.accepts(E2EStep.entries.filterNot { it == E2EStep.Restart }))
        assertFalse(OfflineE2EFixturePolicy.accepts(E2EStep.entries.filterNot { it == E2EStep.DisableNetwork }))
        assertFalse(OfflineE2EFixturePolicy.accepts(E2EStep.entries.filterNot { it == E2EStep.PlayLocalAsset }))
    }
}
