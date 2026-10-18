package com.aruler.android

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aruler.android.ui.dpInt

class GuideActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        setupPage(getString(R.string.guide_title))

        populateSteps()
        bindLegend(
            R.id.legend_add,
            R.drawable.ic_add_place,
            R.string.legend_start,
        )
        bindLegend(
            R.id.legend_add_disabled,
            R.drawable.ic_add_place,
            R.string.legend_start_hint,
            alpha = 0.35f,
        )
        bindLegend(
            R.id.legend_set,
            R.drawable.ic_check_place,
            R.string.legend_end,
        )
        bindLegend(
            R.id.legend_set_disabled,
            R.drawable.ic_check_place,
            R.string.legend_end_hint,
            alpha = 0.35f,
        )
        bindLegend(
            R.id.legend_delete,
            R.drawable.ic_trash,
            R.string.legend_delete,
            tintColor = R.color.alert,
        )
        bindLegend(
            R.id.legend_help,
            R.drawable.ic_help,
            R.string.legend_help,
            tintColor = R.color.text_title,
        )
        bindLegend(
            R.id.legend_settings,
            R.drawable.ic_settings,
            R.string.legend_settings,
            tintColor = R.color.text_title,
        )
        bindLegend(
            R.id.legend_reset,
            R.drawable.ic_restart,
            R.string.legend_reset,
        )
        bindLegend(
            R.id.legend_indicator_green,
            R.drawable.ic_indicator,
            R.string.legend_indicator_green,
            tintColor = R.color.fine,
        )
        bindLegend(
            R.id.legend_indicator_red,
            R.drawable.ic_indicator,
            R.string.legend_indicator_red,
            tintColor = R.color.alert,
        )
    }

    private fun populateSteps() {
        val container = findViewById<LinearLayout>(R.id.guide_steps)
        val steps = listOf(
            R.string.guide_step_1,
            R.string.guide_step_2,
            R.string.guide_step_3,
            R.string.guide_step_4,
            R.string.guide_step_5,
            R.string.guide_step_6,
        )

        steps.forEachIndexed { index, stringRes ->
            val text = TextView(this).apply {
                setText(stringRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(this@GuideActivity, R.color.text_primary))
                setLineSpacing(dpInt(3f).toFloat(), 1f)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            if (index > 0) {
                params.topMargin = dpInt(8f)
            }
            container.addView(text, params)
        }
    }

    private fun bindLegend(
        rowId: Int,
        iconRes: Int,
        captionRes: Int,
        tintColor: Int? = null,
        alpha: Float = 1f,
    ) {
        val row = findViewById<View>(rowId)
        val icon = row.findViewById<ImageView>(R.id.legend_icon)
        icon.setImageResource(iconRes)
        icon.alpha = alpha
        icon.imageTintList = tintColor?.let {
            ColorStateList.valueOf(ContextCompat.getColor(this, it))
        }
        row.findViewById<TextView>(R.id.legend_text).setText(captionRes)
    }
}
