package com.ekkus.offlineytplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryItemActionPolicyTest {
    @Test
    fun requiredActionsAreVisibleAndRenameIsConditional() {
        val enabled = LibraryItemActionPolicy(renameEnabled = true)
        assertEquals(
            listOf(
                LibraryItemAction.Play,
                LibraryItemAction.Details,
                LibraryItemAction.Rename,
                LibraryItemAction.Remove,
            ),
            enabled.visibleActions,
        )
        assertFalse(LibraryItemActionPolicy(renameEnabled = false).visibleActions.contains(LibraryItemAction.Rename))
    }

    @Test
    fun removalIsExplicitConfirmedAndNotSwipeOnly() {
        val policy = LibraryItemActionPolicy(renameEnabled = false)
        assertTrue(policy.requiresConfirmation(LibraryItemAction.Remove))
        assertFalse(policy.requiresConfirmation(LibraryItemAction.Play))
        assertFalse(LibraryItemActionPolicy.DestructiveActionIsSwipeOnly)
        assertTrue(LibraryItemActionPolicy.RemoveDeletesDeviceCopy)
    }
}
