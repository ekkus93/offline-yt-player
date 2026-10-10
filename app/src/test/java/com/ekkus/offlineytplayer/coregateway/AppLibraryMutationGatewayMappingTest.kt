package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLibraryMutationGatewayMappingTest {
    private data class BooleanRecord(val removed: Boolean)
    private data class WrongTypeRecord(val removed: String)

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
