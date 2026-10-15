package com.ekkus.offlineytplayer.downloads

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real [android.os.PersistableBundle] behavior, which the plain JVM unit-test stubs cannot provide. */
@RunWith(AndroidJUnit4::class)
class DownloadSchedulerExtrasInstrumentedTest {
    @Test
    fun uidtJobExtrasCarryOnlyTheDurableQueueIdentity() {
        val extras = DownloadExecutionSchedulerPolicy.durableQueueItemExtras("queue-item-1")

        assertEquals(setOf(DownloadUserInitiatedJobService.ExtraQueueItemId), extras.keySet())
        assertEquals("queue-item-1", extras.getString(DownloadUserInitiatedJobService.ExtraQueueItemId))
    }
}
