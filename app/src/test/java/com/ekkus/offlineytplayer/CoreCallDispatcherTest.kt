package com.ekkus.offlineytplayer

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCallDispatcherTest {
    @Test
    fun blockingCoreCallsExecuteOffCallingThread() {
        val callingThread = Thread.currentThread().name
        CoreCallDispatcher().use { dispatcher ->
            val workerThread = dispatcher.submit { Thread.currentThread().name }.get(2, TimeUnit.SECONDS)
            assertNotEquals(callingThread, workerThread)
            assertEquals("offline-yt-core-worker", workerThread)
        }
    }

    @Test
    fun coreCallConcurrencyIsBoundedToSingleWorker() {
        CoreCallDispatcher().use { dispatcher ->
            val first = dispatcher.submit {
                Thread.sleep(25)
                Thread.currentThread().name
            }
            val second = dispatcher.submit { Thread.currentThread().name }
            assertTrue(first.get(2, TimeUnit.SECONDS).isNotBlank())
            assertEquals(first.get(), second.get(2, TimeUnit.SECONDS))
        }
    }
}
