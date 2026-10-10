package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLibraryMutationGatewayMappingTest {
    private data class MutationErrorRecord(val errorMessage: String?)
    private data class WrongMutationErrorRecord(val errorMessage: Int)
    private data class BooleanRecord(val removed: Boolean)
    private data class WrongTypeRecord(val removed: String)

    @Test
    fun nullableMutationErrorPropertyAcceptsExplicitNullAndString() {
        assertNull(readRequiredNullableMutationError(MutationErrorRecord(null)))
        assertEquals("failed", readRequiredNullableMutationError(MutationErrorRecord("failed")))
    }

    @Test(expected = IllegalStateException::class)
    fun nullableMutationErrorPropertyRejectsMissingBinding() {
        readRequiredNullableMutationError(Any())
    }

    @Test(expected = IllegalStateException::class)
    fun nullableMutationErrorPropertyRejectsWrongType() {
        readRequiredNullableMutationError(WrongMutationErrorRecord(5))
    }

    @Test
    fun requiredBooleanMappingReadsGeneratedBoolean() {
        assertTrue(readRequiredBooleanMutation(BooleanRecord(true), "removed"))
        assertFalse(readRequiredBooleanMutation(BooleanRecord(false), "removed"))
    }

    @Test(expected = IllegalStateException::class)
    fun requiredBooleanMappingFailsClosedWhenPropertyIsMissing() {
        readRequiredBooleanMutation(Any(), "removed")
    }

    @Test(expected = IllegalStateException::class)
    fun requiredBooleanMappingFailsClosedWhenPropertyHasWrongType() {
        readRequiredBooleanMutation(WrongTypeRecord("false"), "removed")
    }
}
