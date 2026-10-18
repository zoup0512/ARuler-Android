package com.aruler.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

data class ScreenLabel(val x: Float, val y: Float, val text: String)

/**
 * Draws the distance labels that float at the midpoint of every measurement line.
 */
class MeasurementLabelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private var labels: List<ScreenLabel> = emptyList()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics,
        )
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
        setShadowLayer(4f * density, 0f, 1.5f * density, 0xCC000000.toInt())
    }

    private val verticalOffset = 18f * density

    init {
        isClickable = false
        isFocusable = false
    }

    fun setLabels(newLabels: List<ScreenLabel>) {
        labels = newLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (label in labels) {
            if (label.x < 0f || label.x > width || label.y < 0f || label.y > height) continue
            canvas.drawText(label.text, label.x, label.y - verticalOffset, textPaint)
        }
    }
}
