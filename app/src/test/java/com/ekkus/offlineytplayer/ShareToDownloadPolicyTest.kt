package com.ekkus.offlineytplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareToDownloadPolicyTest {
    @Test
    fun sharedUrlOpensDownloadSetupDirectly() {
        val route = ShareToDownloadPolicy.initialRoute("https://youtu.be/dQw4w9WgXcQ")

        assertTrue(ShareToDownloadPolicy.OpensDownloadSetupDirectly)
        assertTrue(route is ShareToDownloadRoute.DownloadSetup)
        route as ShareToDownloadRoute.DownloadSetup
        assertEquals("https://youtu.be/dQw4w9WgXcQ", route.url)
    }

    @Test
    fun downloadSetupRoutePreservesFixedControlLayoutAndLibraryBackStack() {
        val route = ShareToDownloadPolicy.initialRoute(" https://example.invalid/video ")

        assertTrue(route is ShareToDownloadRoute.DownloadSetup)
        route as ShareToDownloadRoute.DownloadSetup
        assertEquals("https://example.invalid/video", route.url)
        assertEquals(ShareBackStackDestination.Library, route.backStackDestination)
        assertTrue(route.preservesFixedControlLayout)
        assertTrue(ShareToDownloadPolicy.PreservesFixedControlLayout)
        assertTrue(ShareToDownloadPolicy.ClearsAmbiguousBackStack)
    }

    @Test
    fun missingSharedUrlFallsBackToLibrary() {
        assertEquals(ShareToDownloadRoute.Library, ShareToDownloadPolicy.initialRoute(null))
        assertEquals(ShareToDownloadRoute.Library, ShareToDownloadPolicy.initialRoute("  "))
    }
}
