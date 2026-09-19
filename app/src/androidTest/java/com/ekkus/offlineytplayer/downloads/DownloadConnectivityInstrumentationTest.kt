package com.ekkus.offlineytplayer.downloads

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side coverage for the RMD-508 connectivity transition contract. The test intentionally
 * avoids mutating the device network stack; instead it verifies the production observer can bind to
 * Android's ConnectivityManager, then exercises the same policy states emitted by the observer
 * through the production gate.
 */
@RunWith(AndroidJUnit4::class)
class DownloadConnectivityInstrumentationTest {
    @Test
    fun observerCanBindToConnectivityManagerAndPolicyStatesDriveGateTransitions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        assertNotNull(manager)

        val gateway = com.ekkus.offlineytplayer.coregateway.FakeDownloadControlGateway()
        val scheduler = RecordingScheduler()
        val gate = DownloadConnectivityGate(gateway, scheduler)
        val work = DownloadConnectivityWorkItem(
            queueItemId = "instrumented-wifi",
            estimatedDownloadBytes = 1024,
            networkPreference = DownloadNetworkPreference.WifiOnly,
        )

        val noNetwork = gate.reconcileActiveWork(work, DownloadConnectivity.None)
        assertEquals(DownloadConnectivityGateStatus.PausedForConnectivity, noNetwork.status)
        assertEquals(listOf("instrumented-wifi"), gateway.pausedJobIds)
        assertTrue(gate.waitingQueueItemIds().contains("instrumented-wifi"))

        val metered = gate.onConnectivityChanged(DownloadConnectivity.Metered).single()
        assertEquals(DownloadConnectivityGateStatus.StillWaiting, metered.status)
        assertTrue(scheduler.requests.isEmpty())

        val unmetered = gate.onConnectivityChanged(DownloadConnectivity.Unmetered).single()
        assertEquals(DownloadConnectivityGateStatus.Rescheduled, unmetered.status)
        assertEquals("instrumented-wifi", scheduler.requests.single().queueItemId)
        assertEquals(DownloadNetworkPreference.WifiOnly, scheduler.requests.single().networkPreference)
    }

    private class RecordingScheduler : DownloadExecutionScheduler {
        val requests = mutableListOf<DownloadScheduleRequest>()

        override fun schedule(request: DownloadScheduleRequest): DownloadScheduleResult {
            requests += request
            return DownloadScheduleResult(
                kind = DownloadSchedulerKind.ForegroundServiceFallback,
                accepted = true,
            )
        }
    }
}
