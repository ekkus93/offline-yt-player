package com.ekkus.offlineytplayer.downloads

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadForegroundTimeoutInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @After
    fun clearState() {
        preferences.edit().clear().commit()
    }

    @Test
    fun packagedAppPersistsTimeoutStateBeforeServiceStopPathCanRelyOnIt() {
        preferences.edit().clear().commit()
        assertFalse(preferences.contains(LAST_START_ID_KEY))

        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 41,
            foregroundServiceType = 8,
        )

        assertEquals(41, preferences.getInt(LAST_START_ID_KEY, -1))
        assertEquals(8, preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1))
        assertEquals(1, preferences.getInt(TIMEOUT_COUNT_KEY, 0))
    }

    @Test
    fun packagedAppIncrementsTimeoutCountAcrossRepeatedTimeouts() {
        preferences.edit().clear().commit()

        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 11,
            foregroundServiceType = 1,
        )
        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 12,
            foregroundServiceType = 2,
        )

        assertEquals(12, preferences.getInt(LAST_START_ID_KEY, -1))
        assertEquals(2, preferences.getInt(LAST_FOREGROUND_SERVICE_TYPE_KEY, -1))
        assertEquals(2, preferences.getInt(TIMEOUT_COUNT_KEY, 0))
    }

    @Test
    fun timeoutStateIsOnDiskBeforePersistReturns() {
        preferences.edit().clear().commit()
        DownloadForegroundTimeoutStore.persistTimeout(
            context = context,
            startId = 73,
            foregroundServiceType = 1,
        )

        // Read the actual backing XML rather than SharedPreferences' in-memory
        // cache. A service timeout may stop the process immediately afterward.
        val diskFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$PREFERENCES_NAME.xml")
        val xml = diskFile.readText()
        org.junit.Assert.assertTrue(xml.contains("name=\"$LAST_START_ID_KEY\" value=\"73\""))
        org.junit.Assert.assertTrue(xml.contains("name=\"$LAST_FOREGROUND_SERVICE_TYPE_KEY\" value=\"1\""))
        org.junit.Assert.assertTrue(xml.contains("name=\"$TIMEOUT_COUNT_KEY\" value=\"1\""))
    }

    @Test
    fun timeoutSequencePersistsThenStopsNativeWorkThenStopsForeground() {
        preferences.edit().clear().commit()
        val events = mutableListOf<String>()
        val ownership = ForegroundWorkerOwnership(onStopFailure = {})
        assertTrue(ownership.tryBegin { events += "native-stop-signal" })

        DownloadForegroundTimeoutSequence.run(
            persist = {
                DownloadForegroundTimeoutStore.persistTimeout(context, startId = 5, foregroundServiceType = 1)
                // Durable on disk at the moment this step completes.
                val xml = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$PREFERENCES_NAME.xml").readText()
                assertTrue(xml.contains("name=\"$LAST_START_ID_KEY\" value=\"5\""))
            },
            stopNativeWork = ownership::stop,
            stopForeground = { events += "stopForeground" },
            stopSelf = { events += "stopSelf" },
            observer = { events += "done:${it.name}" },
        )

        assertEquals(
            listOf(
                "done:PersistState",
                "native-stop-signal",
                "done:StopNativeWork",
                "stopForeground",
                "done:StopForeground",
                "stopSelf",
                "done:StopSelf",
            ),
            events,
        )
        assertTrue(ownership.isStopped)
        assertEquals(1, preferences.getInt(TIMEOUT_COUNT_KEY, 0))
    }


    @Test
    fun duplicateTimeoutAndTeardownCallsStopNativeOnceAndRecreatedOwnerIsIndependent() {
        preferences.edit().clear().commit()
        val nativeStopSignals = AtomicInteger()
        val failures = AtomicInteger()
        val oldOwner = ForegroundWorkerOwnership { failures.incrementAndGet() }
        val nativeStop: () -> Unit = { nativeStopSignals.incrementAndGet() }
        assertTrue(oldOwner.tryBegin(nativeStop))
        val gate = CountDownLatch(1)
        val stopped = CountDownLatch(8)
        val workers = (0 until 8).map {
            Thread {
                check(gate.await(10, TimeUnit.SECONDS))
                oldOwner.stop()
                stopped.countDown()
            }.apply { start() }
        }
        val sequence = mutableListOf<DownloadTimeoutStep>()
        try {
            DownloadForegroundTimeoutSequence.run(
                persist = {
                    DownloadForegroundTimeoutStore.persistTimeout(context, 51, 1)
                },
                stopNativeWork = oldOwner::stop,
                stopForeground = {},
                stopSelf = {},
                observer = sequence::add,
            )
            gate.countDown()
            assertTrue(stopped.await(10, TimeUnit.SECONDS))
            workers.forEach { it.join() }
            oldOwner.stop()
            oldOwner.finish(nativeStop)
            assertTrue(oldOwner.isStopped)
            assertFalse(oldOwner.tryBegin { nativeStopSignals.incrementAndGet() })
            assertEquals(1, nativeStopSignals.get())
            assertEquals(0, failures.get())
            assertEquals(
                DownloadTimeoutStep.values().toList(),
                sequence,
            )
            val diskFile = java.io.File(
                context.applicationInfo.dataDir,
                "shared_prefs/$PREFERENCES_NAME.xml",
            )
            assertTrue(diskFile.readText().contains("name=\"$TIMEOUT_COUNT_KEY\" value=\"1\""))
            // A newly constructed service ownership gate must remain usable
            // even when the prior service was destroyed and timed out.
            val newOwner = ForegroundWorkerOwnership { failures.incrementAndGet() }
            val newWorker: () -> Unit = { nativeStopSignals.incrementAndGet() }
            assertTrue(newOwner.tryBegin(newWorker))
            DownloadForegroundTimeoutSequence.run(
                persist = { DownloadForegroundTimeoutStore.persistTimeout(context, 52, 1) },
                stopNativeWork = oldOwner::stop,
                stopForeground = {},
                stopSelf = {},
            )
            assertEquals(1, nativeStopSignals.get())
            assertEquals(2, preferences.getInt(TIMEOUT_COUNT_KEY, 0))
            assertEquals(52, preferences.getInt(LAST_START_ID_KEY, -1))
            assertFalse(newOwner.isStopped)
            newOwner.finish(newWorker)
            assertEquals(0, failures.get())
        } finally {
            gate.countDown()
            workers.forEach { it.join(1_000) }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "download_foreground_timeout"
        const val LAST_START_ID_KEY = "last_start_id"
        const val LAST_FOREGROUND_SERVICE_TYPE_KEY = "last_foreground_service_type"
        const val TIMEOUT_COUNT_KEY = "timeout_count"
    }
}
