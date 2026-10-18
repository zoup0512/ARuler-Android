package com.aruler.android

import org.junit.Assert.assertEquals
import org.junit.Test

class LengthUnitTest {

    @Test
    fun formatsMetersUsingEachUnitRate() {
        assertEquals("100.0cm", LengthUnit.CENTIMETER.format(1f, 1))
        assertEquals("1.0m", LengthUnit.METER.format(1f, 1))
        assertEquals("3.3ft", LengthUnit.FOOT.format(1f, 1))
        assertEquals("39.4in", LengthUnit.INCH.format(1f, 1))
        assertEquals("3.0尺", LengthUnit.CHI.format(1f, 1))
    }

    @Test
    fun displayOrderMatchesIosSegmentedControl() {
        assertEquals(
            listOf("cm", "m", "ft", "in", "尺"),
            LengthUnit.displayOrder.map { it.symbol },
        )
    }
}
