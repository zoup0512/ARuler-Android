package com.aruler.android

import androidx.annotation.StringRes
import java.util.Locale

/**
 * Measurement units match the iOS app's rates and display order.
 */
enum class LengthUnit(
    val symbol: String,
    val rate: Float,
    @param:StringRes val descriptionRes: Int,
) {
    CENTIMETER("cm", 100f, R.string.unit_centimeter_desc),
    METER("m", 1f, R.string.unit_meter_desc),
    FOOT("ft", 3.2808399f, R.string.unit_foot_desc),
    INCH("in", 39.3700787f, R.string.unit_inch_desc),
    CHI("尺", 3f, R.string.unit_chi_desc);

    fun format(meters: Float, decimals: Int): String {
        val converted = meters * rate
        return String.format(Locale.US, "%.${decimals}f%s", converted, symbol)
    }

    companion object {
        val displayOrder: List<LengthUnit> = listOf(CENTIMETER, METER, FOOT, INCH, CHI)

        fun fromKey(key: String?): LengthUnit? = entries.firstOrNull { it.name == key }
    }
}
