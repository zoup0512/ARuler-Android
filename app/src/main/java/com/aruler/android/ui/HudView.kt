package com.aruler.android.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aruler.android.R

/**
 * Small translucent message bubble used for tracking hints.
 */
class HudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val density = resources.displayMetrics.density
    private val messageView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setLineSpacing(4f * density, 1f)
        setPadding(
            (16f * density).toInt(),
            (12f * density).toInt(),
            (16f * density).toInt(),
            (12f * density).toInt(),
        )
        background = ContextCompat.getDrawable(context, R.drawable.bg_hud)
        maxWidth = (300f * density).toInt()
    }

    private val hideRunnable = Runnable { fadeOut() }

    init {
        visibility = GONE
        alpha = 0f
        isClickable = false
        isFocusable = false
        addView(
            messageView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )
    }

    fun showMessage(message: String) {
        if (messageView.text.toString() != message) {
            messageView.text = message
        }
        removeCallbacks(hideRunnable)
        if (visibility != VISIBLE) {
            visibility = VISIBLE
            alpha = 0f
        }
        animate().cancel()
        animate().alpha(1f).setDuration(160L).start()
    }

    fun flashMessage(message: String) {
        showMessage(message)
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, 900L)
    }

    fun hideMessage() {
        removeCallbacks(hideRunnable)
        fadeOut()
    }

    private fun fadeOut() {
        if (visibility != VISIBLE) return
        animate().cancel()
        animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                if (alpha == 0f) {
                    visibility = View.GONE
                }
            }
            .start()
    }
}
