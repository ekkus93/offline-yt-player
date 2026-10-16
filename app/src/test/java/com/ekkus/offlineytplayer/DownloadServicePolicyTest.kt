package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.downloads.DownloadServicePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadServicePolicyTest {
    @Test
    fun serviceIsUserVisibleBoundedAndActionable() {
        assertTrue(DownloadServicePolicy.NotificationId > 0)
        assertTrue(DownloadServicePolicy.DefaultConcurrentDownloads in 1..4)
        assertEquals(4, DownloadServicePolicy.concurrentDownloads(Int.MAX_VALUE, 4))
    }

}
