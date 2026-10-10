package com.ekkus.offlineytplayer

import com.ekkus.offlineytplayer.coregateway.AppCoreGateway
import com.ekkus.offlineytplayer.coregateway.CoreDownloadSnapshot
import com.ekkus.offlineytplayer.coregateway.CoreGatewayResult
import com.ekkus.offlineytplayer.coregateway.CoreLibraryItem
import com.ekkus.offlineytplayer.coregateway.CoreStartupReconciliation
import com.ekkus.offlineytplayer.coregateway.FakeCoreGateway
import com.ekkus.offlineytplayer.ui.DownloadsScreenState
import com.ekkus.offlineytplayer.ui.LibraryScreenState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapCoreReadFailureTest {
    private val privateDetail = "/private/app/source?token=secret"

    @Test fun thrownStartupReconciliationBecomesSanitizedFailure() {
        val gateway = object : AppCoreGateway by FakeCoreGateway() {
            override fun reconcileStartup(): CoreGatewayResult<CoreStartupReconciliation> =
                throw IllegalStateException(privateDetail)
        }
        val error = gateway.reconcileStartupSafely()
        assertEquals("startup_unavailable", error?.kind)
        assertEquals("Startup reconciliation is unavailable.", error?.message)
    }

    @Test fun malformedSuccessfulStartupResultIsNotAccepted() {
        val gateway = FakeCoreGateway(
            startupReconciliation = CoreGatewayResult(null, null),
        )
        assertEquals("startup_unavailable", gateway.reconcileStartupSafely()?.kind)
    }

    @Test fun ordinarySuccessfulStartupRemainsAvailable() {
        assertNull(FakeCoreGateway().reconcileStartupSafely())
    }

    @Test fun thrownInitialLibraryReadPublishesUnavailableInsteadOfLoading() {
        val gateway = object : AppCoreGateway by FakeCoreGateway() {
            override fun listLibrary(query: String?): CoreGatewayResult<List<CoreLibraryItem>> =
                throw IllegalStateException(privateDetail)
        }
        val mapped = gateway.readInitialLibraryStateSafely("query", File("."), null)
        assertTrue(mapped is LibraryScreenState.Failed)
        assertEquals("Library repository data is unavailable.", (mapped as LibraryScreenState.Failed).message)
    }

    @Test fun thrownInitialQueueReadPublishesUnavailableInsteadOfLoading() {
        val gateway = object : AppCoreGateway by FakeCoreGateway() {
            override fun listDownloadQueue(): CoreGatewayResult<List<CoreDownloadSnapshot>> =
                throw IllegalStateException(privateDetail)
        }
        val mapped = gateway.readInitialDownloadsStateSafely(emptyMap())
        assertTrue(mapped is DownloadsScreenState.Failed)
        assertEquals("Download queue data is unavailable.", (mapped as DownloadsScreenState.Failed).message)
    }
}
