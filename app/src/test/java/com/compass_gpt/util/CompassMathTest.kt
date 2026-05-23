package com.compass_gpt.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMathTest {

    @Test
    fun testBearingNormalization() {
        val bearing = 370f
        val normalized = ((bearing % 360f) + 360f) % 360f
        assertEquals(10f, normalized, 0.01f)
    }

    @Test
    fun testNegativeBearingNormalization() {
        val bearing = -10f
        val normalized = ((bearing % 360f) + 360f) % 360f
        assertEquals(350f, normalized, 0.01f)
    }

    @Test
    fun testCoordinateFormatting() {
        val lat = 45.123456
        val formatted = "%.4f".format(lat)
        assertEquals("45.1235", formatted) // rounding up
    }
}