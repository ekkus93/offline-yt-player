package com.ekkus.offlineytplayer.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceBoundsPolicyTest {
    @Test
    fun timeoutsAndBodyBoundsAreFinite() {
        assertTrue(ResourceBoundsPolicy.ConnectTimeoutSeconds in 1..60)
        assertTrue(ResourceBoundsPolicy.ReadTimeoutSeconds in 1..120)
        assertTrue(ResourceBoundsPolicy.bodySizeAllowed(ResourceBoundsPolicy.MaximumMetadataBytes.toLong()))
        assertFalse(ResourceBoundsPolicy.bodySizeAllowed(ResourceBoundsPolicy.MaximumMetadataBytes.toLong() + 1))
    }

    @Test
    fun concurrencyIsBounded() {
        assertTrue(ResourceBoundsPolicy.concurrencyAllowed(ResourceBoundsPolicy.MaximumConcurrentDownloads))
        assertFalse(ResourceBoundsPolicy.concurrencyAllowed(ResourceBoundsPolicy.MaximumConcurrentDownloads + 1))
        assertFalse(ResourceBoundsPolicy.concurrencyAllowed(-1))
    }

    @Test
    fun diskPreflightReservesRecoveryHeadroom() {
        val expected = 64L * 1024L * 1024L
        val enough = expected + ResourceBoundsPolicy.MinimumFreeSpaceReserveBytes
        assertTrue(ResourceBoundsPolicy.hasDiskCapacity(enough, expected))
        assertFalse(ResourceBoundsPolicy.hasDiskCapacity(enough - 1, expected))
        assertFalse(ResourceBoundsPolicy.hasDiskCapacity(-1, expected))
    }
}
