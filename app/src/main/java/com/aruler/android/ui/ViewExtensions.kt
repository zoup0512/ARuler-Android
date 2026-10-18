package com.aruler.android.ui

import android.content.Context
import android.graphics.Paint
import android.widget.TextView

fun TextView.makeUnderlined() {
    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
}

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

fun Context.dpInt(value: Float): Int = (value * resources.displayMetrics.density).toInt()
