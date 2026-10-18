package com.aruler.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.aruler.android.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Coach-mark overlay used for the first launch walkthrough. It dims the screen,
 * highlights the target view, and advances on tap.
 */
class CoachMarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Shape { NONE, CIRCLE, SQUARE }

    data class Step(
        val caption: String,
        val target: View? = null,
        val shape: Shape = Shape.NONE,
    )

    private val density = resources.displayMetrics.density
    private val pad = 10f * density

    private val dimPaint = Paint().apply { color = 0xB3000000.toInt() }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f * density
        color = 0x4DFFD60A
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFFFD60A.toInt()
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE61C1C1E.toInt()
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            15f,
            resources.displayMetrics,
        )
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            13f,
            resources.displayMetrics,
        )
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f * density, 0f, 1f * density, 0x99000000.toInt())
    }
    private val hintText = context.getString(R.string.coach_tap_hint)

    private var steps: List<Step> = emptyList()
    private var stepIndex = 0
    private var onFinished: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        visibility = GONE
    }

    fun isActive(): Boolean = visibility == VISIBLE

    fun start(newSteps: List<Step>, finished: () -> Unit) {
        if (newSteps.isEmpty()) {
            finished()
            return
        }
        steps = newSteps
        stepIndex = 0
        onFinished = finished
        visibility = VISIBLE
        alpha = 0f
        requestLayout()
        animate().alpha(1f).setDuration(180L).start()
        announceCurrentStep()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        advance()
        return true
    }

    private fun advance() {
        if (steps.isEmpty()) return
        if (stepIndex >= steps.lastIndex) {
            val callback = onFinished
            visibility = GONE
            steps = emptyList()
            stepIndex = 0
            onFinished = null
            invalidate()
            callback?.invoke()
            return
        }
        stepIndex++
        announceCurrentStep()
        invalidate()
    }

    private fun announceCurrentStep() {
        val caption = steps.getOrNull(stepIndex)?.caption ?: return
        contentDescription = caption
        announceForAccessibility(caption)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (steps.isEmpty() || width == 0 || height == 0) return

        val step = steps[stepIndex]
        val targetRect = resolveTargetRect(step)

        drawDimmedBackdrop(canvas, step.shape, targetRect)
        drawHighlight(canvas, step.shape, targetRect)
        drawBubble(canvas, step.caption, targetRect)

        canvas.drawText(
            hintText,
            width / 2f,
            height - 30f * density,
            hintPaint,
        )
    }

    private fun resolveTargetRect(step: Step): RectF? {
        val target = step.target ?: return null
        if (target.visibility != View.VISIBLE) return null
        if (target.width == 0 || target.height == 0) return null

        val targetLocation = IntArray(2)
        val selfLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        getLocationOnScreen(selfLocation)

        val left = (targetLocation[0] - selfLocation[0]).toFloat()
        val top = (targetLocation[1] - selfLocation[1]).toFloat()
        val rect = RectF(left, top, left + target.width, top + target.height)
        rect.inset(-pad, -pad)
        return rect
    }

    private fun drawDimmedBackdrop(canvas: Canvas, shape: Shape, targetRect: RectF?) {
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (targetRect != null) {
            when (shape) {
                Shape.CIRCLE -> canvas.drawCircle(
                    targetRect.centerX(),
                    targetRect.centerY(),
                    max(targetRect.width(), targetRect.height()) / 2f,
                    clearPaint,
                )

                Shape.SQUARE -> canvas.drawRoundRect(
                    targetRect,
                    12f * density,
                    12f * density,
                    clearPaint,
                )

                Shape.NONE -> Unit
            }
        }
        canvas.restoreToCount(layer)
    }

    private fun drawHighlight(canvas: Canvas, shape: Shape, targetRect: RectF?) {
        if (targetRect == null) return
        when (shape) {
            Shape.CIRCLE -> {
                val radius = max(targetRect.width(), targetRect.height()) / 2f
                val cx = targetRect.centerX()
                val cy = targetRect.centerY()
                canvas.drawCircle(cx, cy, radius, glowPaint)
                canvas.drawCircle(cx, cy, radius, ringPaint)
            }

            Shape.SQUARE -> {
                val radius = 12f * density
                canvas.drawRoundRect(targetRect, radius, radius, glowPaint)
                canvas.drawRoundRect(targetRect, radius, radius, ringPaint)
            }

            Shape.NONE -> Unit
        }
    }

    private fun drawBubble(canvas: Canvas, caption: String, targetRect: RectF?) {
        val margin = 16f * density
        val maxTextWidth = min(width - 2f * margin - 32f * density, 300f * density)
        if (maxTextWidth <= 0f) return

        val layout = StaticLayout.Builder.obtain(
            caption,
            0,
            caption.length,
            textPaint,
            maxTextWidth.toInt(),
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(2f * density, 1f)
            .setIncludePad(false)
            .build()

        var widestLine = 0f
        for (line in 0 until layout.lineCount) {
            widestLine = max(widestLine, layout.getLineWidth(line))
        }

        val padH = 16f * density
        val padV = 12f * density
        val bubbleWidth = min(maxTextWidth, ceil(widestLine)) + 2f * padH
        val bubbleHeight = layout.height + 2f * padV

        var left = (width - bubbleWidth) / 2f
        var top = (height - bubbleHeight) / 2f
        var pointerPointsUp = false

        if (targetRect != null) {
            left = targetRect.centerX() - bubbleWidth / 2f
            val above = targetRect.top - margin - bubbleHeight - 12f * density
            if (above >= margin) {
                top = above
                pointerPointsUp = false
            } else {
                top = targetRect.bottom + 12f * density
                pointerPointsUp = true
            }
        }

        left = left.coerceIn(margin, max(margin, width - margin - bubbleWidth))
        top = top.coerceIn(margin, max(margin, height - margin - bubbleHeight - 30f * density))

        val bubbleRect = RectF(left, top, left + bubbleWidth, top + bubbleHeight)
        canvas.drawRoundRect(bubbleRect, 14f * density, 14f * density, bubblePaint)

        if (targetRect != null) {
            val pointerX = targetRect.centerX().coerceIn(
                bubbleRect.left + 22f * density,
                bubbleRect.right - 22f * density,
            )
            val halfWidth = 7f * density
            val heightPx = 8f * density
            val path = Path()
            if (pointerPointsUp) {
                path.moveTo(pointerX, bubbleRect.top - heightPx)
                path.lineTo(pointerX - halfWidth, bubbleRect.top)
                path.lineTo(pointerX + halfWidth, bubbleRect.top)
            } else {
                path.moveTo(pointerX, bubbleRect.bottom + heightPx)
                path.lineTo(pointerX - halfWidth, bubbleRect.bottom)
                path.lineTo(pointerX + halfWidth, bubbleRect.bottom)
            }
            path.close()
            canvas.drawPath(path, bubblePaint)
        }

        canvas.save()
        canvas.translate(bubbleRect.left + padH, bubbleRect.top + padV)
        layout.draw(canvas)
        canvas.restore()
    }
}
