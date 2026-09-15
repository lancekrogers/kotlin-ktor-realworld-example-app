package io.realworld.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CiRedProbeTest {
    @Test
    fun `ci red-path probe - must fail`() {
        assertEquals("CI must report this failure as an annotation", 1, 2)
    }
}
