package com.ekkus.offlineytplayer.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ekkus.offlineytplayer.MainActivity
import com.ekkus.offlineytplayer.MainActivityDependencyOverrides
import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreGatewayError
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.CoreStartupReconciliation
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RMD-1802: pins that [MainActivity] runs startup reconciliation before any Library/Downloads
 * read, and that a failed reconciliation suppresses those reads. Replaces the review-only claim
 * in the source-text test audit; observes real calls on a fake core gateway, no source matching.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityStartupOrderingInstrumentedTest {
    private val events = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        events.clear()
        MainActivityDependencyOverrides.clearForInstrumentation()
    }

    @After
    fun tearDown() {
        MainActivityDependencyOverrides.clearForInstrumentation()
    }

    @Test
    fun reconcileRunsBeforeEveryLibraryAndDownloadsRead() {
        install(reconcile = {
            events += "reconcile"
            CoreGatewayResult(CoreStartupReconciliation(jobsRequeued = 0), null)
        })
        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil { "listLibrary" in events && "listDownloadQueue" in events }
            val snapshot = events.toList()
            assertEquals("reconcile must be the first core call: $snapshot", "reconcile", snapshot.first())
            assertEquals("reconcile runs exactly once: $snapshot", 1, snapshot.count { it == "reconcile" })
            assertTrue("reads must follow reconcile: $snapshot", snapshot.drop(1).all { it.startsWith("list") })
        }
    }

    @Test
    fun failedReconcileResultYieldsNoLibraryOrDownloadsReads() {
        install(reconcile = {
            events += "reconcile"
            CoreGatewayResult(null, CoreGatewayError("storage", "boom", false))
        })
        assertNoReadsAfterFailedReconcile()
    }

    @Test
    fun throwingReconcileYieldsNoLibraryOrDownloadsReads() {
        install(reconcile = {
            events += "reconcile"
            error("simulated FFI failure")
        })
        assertNoReadsAfterFailedReconcile()
    }

    private fun assertNoReadsAfterFailedReconcile() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitUntil { "reconcile" in events }
            // The state refresher polls every second when started; give it several chances to
            // (wrongly) read, which production must not do after a failed reconcile.
            Thread.sleep(3_500)
            assertEquals(listOf("reconcile"), events.toList())
        }
    }

    private fun install(reconcile: () -> CoreGatewayResult<CoreStartupReconciliation>) {
        MainActivityDependencyOverrides.installCoreForInstrumentation {
            RecordingCoreGateway(events, reconcile)
        }
    }

    private fun waitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out; events=${events.toList()}" }
            Thread.sleep(50)
        }
    }

    private class RecordingCoreGateway(
        private val events: MutableList<String>,
        private val reconcile: () -> CoreGatewayResult<CoreStartupReconciliation>,
    ) : AppCoreGateway {
        override fun reconcileStartup() = reconcile()

        override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> {
            events += "listLibrary"
            return CoreGatewayResult(emptyList(), null)
        }

        override fun getLibraryItem(itemId: String): CoreGatewayResult<CoreLibraryItem?> {
            events += "getLibraryItem"
            return CoreGatewayResult(null, null)
        }

        override fun deleteLibraryItem(itemId: String): CoreGatewayResult<Boolean> {
            events += "deleteLibraryItem"
            return CoreGatewayResult(false, null)
        }

        override fun listDownloadQueue(): CoreGatewayResult<List<CoreDownloadSnapshot>> {
            events += "listDownloadQueue"
            return CoreGatewayResult(emptyList(), null)
        }

        override fun close() = Unit
    }
}
