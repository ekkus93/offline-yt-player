package com.ekkus.offlineytplayer.coregateway

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GeneratedNumericConversionTest {
    @Test
    fun acceptsUnsignedAndSignedValuesWithinIntRange() {
        assertEquals(0, generatedNumericAsInt(0uL))
        assertEquals(1, generatedNumericAsInt(1u))
        assertEquals(Int.MAX_VALUE, generatedNumericAsInt(Int.MAX_VALUE.toULong()))
        assertEquals(Int.MAX_VALUE, generatedNumericAsInt(Int.MAX_VALUE.toLong()))
    }

    @Test
    fun rejectsValuesThatWouldWrapWhenNarrowedToInt() {
        assertInvalid { generatedNumericAsInt(Int.MAX_VALUE.toULong() + 1uL) }
        assertInvalid { generatedNumericAsInt(UInt.MAX_VALUE) }
        assertInvalid { generatedNumericAsInt(ULong.MAX_VALUE) }
        assertInvalid { generatedNumericAsInt(-1L) }
    }

    @Test
    fun retainsLongRangeChecksForOptionalAndRequiredUnsignedFields() {
        assertEquals(Long.MAX_VALUE, generatedNumericAsLong(Long.MAX_VALUE.toULong()))
        assertEquals(UInt.MAX_VALUE.toLong(), generatedNumericAsLong(UInt.MAX_VALUE))
        assertInvalid { generatedNumericAsLong(Long.MAX_VALUE.toULong() + 1uL) }
        assertInvalid { generatedNumericAsLong(ULong.MAX_VALUE) }
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected out-of-range generated number to be rejected")
        } catch (_: IllegalArgumentException) {
            // Fail closed rather than silently wrapping a generated unsigned value.
        }
    }
}
