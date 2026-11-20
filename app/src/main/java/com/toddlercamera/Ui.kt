package com.toddlercamera

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup

object Ui {
    val BLUE = Color.rgb(50, 130, 255)
    val PURPLE = Color.rgb(150, 90, 255)
    val ORANGE = Color.rgb(255, 150, 30)
    val RED = Color.rgb(230, 40, 40)
    val DARK = Color.argb(120, 0, 0, 0)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** A big round button with a drawn icon. */
    fun roundButton(ctx: Context, icon: Icon, color: Int, sizeDp: Int, description: String): RoundIconButton =
        RoundIconButton(ctx, icon, color).apply {
            contentDescription = description
            layoutParams = ViewGroup.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
        }
}
