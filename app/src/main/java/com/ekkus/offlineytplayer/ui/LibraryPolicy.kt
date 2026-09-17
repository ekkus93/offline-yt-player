package com.ekkus.offlineytplayer.ui

internal enum class LibraryLayoutMode {
    List,
    Grid,
}

internal data class LibraryItemSummary(
    val itemId: String,
    val title: String,
    val source: String,
)

internal object LibraryScreenPolicy {
    const val HasFixedTopControls = true
    const val UsesFixedBottomNavigation = true
    const val OnlyItemRegionScrolls = true
    const val SupportsListAndGrid = true
    const val SupportsSearchAndFilter = true
    const val EmptyStateHasAddAction = true

    fun visibleItems(
        items: List<LibraryItemSummary>,
        query: String,
        sourceFilter: String? = null,
    ): List<LibraryItemSummary> {
        val normalizedQuery = query.trim().lowercase()
        val normalizedSource = sourceFilter?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
        return items.filter { item ->
            val matchesQuery = normalizedQuery.isEmpty() ||
                item.title.lowercase().contains(normalizedQuery) ||
                item.source.lowercase().contains(normalizedQuery)
            val matchesSource = normalizedSource == null || item.source.lowercase() == normalizedSource
            matchesQuery && matchesSource
        }
    }

    fun emptyStateShowsAdd(items: List<LibraryItemSummary>): Boolean = items.isEmpty()
}
