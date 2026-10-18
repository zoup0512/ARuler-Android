package com.aruler.android

import android.content.Context

object Prefs {

    private const val FILE = "aruler_prefs"
    private const val KEY_LENGTH_UNIT = "length_unit"
    private const val KEY_GUIDE_SHOWN = "guide_shown"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lengthUnit(context: Context): LengthUnit {
        return LengthUnit.fromKey(prefs(context).getString(KEY_LENGTH_UNIT, null))
            ?: LengthUnit.CENTIMETER
    }

    fun setLengthUnit(context: Context, unit: LengthUnit) {
        prefs(context).edit().putString(KEY_LENGTH_UNIT, unit.name).apply()
    }

    fun hasShownGuide(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GUIDE_SHOWN, false)
    }

    fun markGuideShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_GUIDE_SHOWN, true).apply()
    }
}
