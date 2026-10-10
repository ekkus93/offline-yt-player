package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScreenPolicyTest {
    private val items = listOf(
        LibraryItemSummary("1", "Rust async explained", "YouTube"),
        LibraryItemSummary("2", "Kotlin coroutines", "Fixture"),
    )

    @Test
    fun filterHelperMatchesTitleAndSourceWithoutFabricatedCapabilities() {
        assertEquals(listOf(items[0]), LibraryScreenPolicy.visibleItems(items, "RUST"))
        assertEquals(listOf(items[1]), LibraryScreenPolicy.visibleItems(items, "", "fixture"))
        assertEquals(emptyList<LibraryItemSummary>(), LibraryScreenPolicy.visibleItems(items, "rust", "fixture"))
    }

    @Test
    fun emptyStateHelperOnlyReturnsTrueForEmptyLibrary() {
        assertTrue(LibraryScreenPolicy.emptyStateShowsAdd(emptyList()))
        assertFalse(LibraryScreenPolicy.emptyStateShowsAdd(items))
    }
}
