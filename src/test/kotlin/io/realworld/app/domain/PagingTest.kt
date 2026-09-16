package io.realworld.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PagingTest {

    @Test
    fun parse_defaultsToTwentyAndZero() {
        val paging = Paging.parse(null, null)
        assertEquals(Paging(20, 0), paging)
    }

    @Test
    fun parse_acceptsLimitOne() {
        val paging = Paging.parse("1", null)
        assertEquals(Paging(1, 0), paging)
    }

    @Test
    fun parse_acceptsLimitOneHundred() {
        val paging = Paging.parse("100", null)
        assertEquals(Paging(100, 0), paging)
    }

    @Test
    fun parse_rejectsLimitZero() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            Paging.parse("0", null)
        }
        assertEquals("limit must be between 1 and 100.", ex.message)
    }

    @Test
    fun parse_rejectsLimitAboveMax() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            Paging.parse("101", null)
        }
        assertEquals("limit must be between 1 and 100.", ex.message)
    }

    @Test
    fun parse_rejectsNegativeOffset() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            Paging.parse(null, "-1")
        }
        assertEquals("offset must not be negative.", ex.message)
    }

    @Test
    fun parse_rejectsNonIntegerLimit() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            Paging.parse("abc", null)
        }
        assertEquals("limit must be an integer.", ex.message)
    }
}
