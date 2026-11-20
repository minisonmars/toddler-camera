package com.toddlercamera

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.Button

object Ui {
    val BLUE = Color.rgb(50, 130, 255)
    val PURPLE = Color.rgb(150, 90, 255)
    val ORANGE = Color.rgb(255, 150, 30)
    val RED = Color.rgb(230, 40, 40)
    val DARK = Color.argb(120, 0, 0, 0)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** A big round button with an emoji or short label. */
    fun roundButton(ctx: Context, label: String, color: Int, sizeDp: Int, textSp: Float = 34f): Button =
        Button(ctx).apply {
            text = label
            textSize = textSp
            setTextColor(Color.WHITE)
            isAllCaps = false
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            stateListAnimator = null
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = ViewGroup.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        }
}
