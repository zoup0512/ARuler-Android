package com.aruler.android.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aruler.android.LengthUnit
import com.aruler.android.R

/**
 * Compact five-segment control matching the iOS settings row.
 */
class UnitSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onUnitChanged: ((LengthUnit) -> Unit)? = null

    private var selectedUnit: LengthUnit = LengthUnit.CENTIMETER

    private val density = resources.displayMetrics.density

    init {
        orientation = HORIZONTAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_segment_container)
        clipToOutline = true
        setPadding(
            (1f * density).toInt(),
            (1f * density).toInt(),
            (1f * density).toInt(),
            (1f * density).toInt(),
        )
        rebuild()
    }

    fun setUnit(unit: LengthUnit, notify: Boolean = false) {
        if (selectedUnit == unit && childCount > 0) return
        selectedUnit = unit
        rebuild()
        if (notify) onUnitChanged?.invoke(unit)
    }

    fun getUnit(): LengthUnit = selectedUnit

    private fun rebuild() {
        removeAllViews()
        for (unit in LengthUnit.displayOrder) {
            val option = TextView(context).apply {
                text = unit.symbol
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (unit == selectedUnit) R.color.white else R.color.theme,
                    ),
                )
                background = if (unit == selectedUnit) {
                    ContextCompat.getDrawable(context, R.drawable.bg_segment_selected)
                } else {
                    null
                }
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(
                    R.string.settings_length_unit,
                ) + " " + context.getString(unit.descriptionRes)
                setOnClickListener {
                    setUnit(unit, notify = true)
                }
            }
            addView(
                option,
                LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
            )
        }
    }
}
