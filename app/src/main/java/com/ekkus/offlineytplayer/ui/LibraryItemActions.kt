package com.ekkus.offlineytplayer.ui

internal enum class LibraryItemAction {
    Play,
    Details,
    Rename,
    Remove,
}

internal data class LibraryItemActionPolicy(
    val renameEnabled: Boolean,
) {
    val visibleActions: List<LibraryItemAction>
        get() = buildList {
            add(LibraryItemAction.Play)
            add(LibraryItemAction.Details)
            if (renameEnabled) add(LibraryItemAction.Rename)
            add(LibraryItemAction.Remove)
        }

    fun requiresConfirmation(action: LibraryItemAction): Boolean = action == LibraryItemAction.Remove

    companion object {
        const val DestructiveActionIsSwipeOnly = false
        const val RemoveDeletesDeviceCopy = true
    }
}
