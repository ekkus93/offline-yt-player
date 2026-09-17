package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScreenPolicyTest {
    private val items = listOf(
        LibraryItemSummary("1", "Rust async explained", "YouTube"),
        LibraryItemSummary("2", "Kotlin coroutines", "Fixture"),
    )

    @Test
    fun libraryKeepsChromeFixedAndItemRegionBounded() {
        assertTrue(LibraryScreenPolicy.HasFixedTopControls)
        assertTrue(LibraryScreenPolicy.UsesFixedBottomNavigation)
        assertTrue(LibraryScreenPolicy.OnlyItemRegionScrolls)
    }

    @Test
    fun librarySupportsListGridSearchAndFilter() {
        assertTrue(LibraryScreenPolicy.SupportsListAndGrid)
        assertEquals(listOf(LibraryLayoutMode.List, LibraryLayoutMode.Grid), LibraryLayoutMode.entries)
        assertTrue(LibraryScreenPolicy.SupportsSearchAndFilter)
        assertEquals(listOf(items[0]), LibraryScreenPolicy.visibleItems(items, "rust"))
        assertEquals(listOf(items[1]), LibraryScreenPolicy.visibleItems(items, "", "fixture"))
        assertEquals(emptyList<LibraryItemSummary>(), LibraryScreenPolicy.visibleItems(items, "rust", "fixture"))
    }

    @Test
    fun emptyLibraryProvidesVisibleAddAction() {
        assertTrue(LibraryScreenPolicy.EmptyStateHasAddAction)
        assertTrue(LibraryScreenPolicy.emptyStateShowsAdd(emptyList()))
    }
}
