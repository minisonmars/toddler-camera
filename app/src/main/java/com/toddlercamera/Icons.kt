package com.toddlercamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class Icon { NONE, CAMERA, VIDEO, FLIP, GALLERY, ARROW_LEFT, ARROW_RIGHT, CLOSE, GEAR, STOP, PLAY }

/**
 * A round button with an icon drawn in code, so every icon has the same size,
 * weight and alignment on every phone (emoji and symbol characters don't).
 */
class RoundIconButton(context: Context, icon: Icon, fill: Int) : View(context) {

    var icon: Icon = icon
        set(value) {
            field = value
            invalidate()
        }
    var fill: Int = fill
        set(value) {
            field = value
            invalidate()
        }

    /** Colour of an outer ring, or 0 for none. */
    var ring: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    init {
        isFocusable = true // so a remote control can reach it on a TV
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val c = s / 2f
        canvas.save()
        if (isPressed) canvas.scale(0.92f, 0.92f, c, c)

        val margin = s * 0.04f
        val discR = c - margin

        // Focus ring (visible when moving around with a remote)
        if (isFocused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.05f
            paint.color = Color.rgb(255, 235, 60)
            canvas.drawCircle(c, c, c - s * 0.025f, paint)
        }

        // Disc and optional ring
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawCircle(c, c, discR - if (isFocused) s * 0.03f else 0f, paint)
        if (ring != 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.07f
            paint.color = ring
            canvas.drawCircle(c, c, discR - s * 0.035f - if (isFocused) s * 0.03f else 0f, paint)
        }

        // Icon, drawn in a 0..1 box scaled to the button
        canvas.save()
        canvas.scale(s, s)
        paint.color = Color.WHITE
        drawIcon(canvas)
        canvas.restore()

        canvas.restore()
    }

    private fun fillStyle() {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
    }

    private fun strokeStyle(width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = Color.WHITE
    }

    private fun drawIcon(canvas: Canvas) {
        when (icon) {
            Icon.NONE -> {}

            Icon.CAMERA -> {
                fillStyle()
                rect.set(0.14f, 0.32f, 0.86f, 0.78f)
                canvas.drawRoundRect(rect, 0.08f, 0.08f, paint)
                rect.set(0.36f, 0.22f, 0.64f, 0.36f)
                canvas.drawRoundRect(rect, 0.04f, 0.04f, paint)
                paint.color = fill
                canvas.drawCircle(0.5f, 0.55f, 0.16f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(0.5f, 0.55f, 0.09f, paint)
            }

            Icon.VIDEO -> {
                fillStyle()
                rect.set(0.12f, 0.30f, 0.62f, 0.70f)
                canvas.drawRoundRect(rect, 0.06f, 0.06f, paint)
                path.reset()
                path.moveTo(0.67f, 0.50f)
                path.lineTo(0.88f, 0.34f)
                path.lineTo(0.88f, 0.66f)
                path.close()
                canvas.drawPath(path, paint)
            }

            Icon.FLIP -> {
                val r = 0.26f
                rect.set(0.5f - r, 0.5f - r, 0.5f + r, 0.5f + r)
                for (start in floatArrayOf(25f, 205f)) {
                    strokeStyle(0.07f)
                    canvas.drawArc(rect, start, 130f, false, paint)
                    // Arrow head at the end of the arc
                    val a = Math.toRadians((start + 130f).toDouble())
                    val px = 0.5f + r * cos(a).toFloat()
                    val py = 0.5f + r * sin(a).toFloat()
                    val tx = -sin(a).toFloat()
                    val ty = cos(a).toFloat()
                    val nx = cos(a).toFloat()
                    val ny = sin(a).toFloat()
                    path.reset()
                    path.moveTo(px + tx * 0.13f, py + ty * 0.13f)
                    path.lineTo(px + nx * 0.11f, py + ny * 0.11f)
                    path.lineTo(px - nx * 0.11f, py - ny * 0.11f)
                    path.close()
                    fillStyle()
                    canvas.drawPath(path, paint)
                }
            }

            Icon.GALLERY -> {
                fillStyle()
                rect.set(0.14f, 0.20f, 0.86f, 0.80f)
                canvas.drawRoundRect(rect, 0.07f, 0.07f, paint)
                paint.color = fill
                canvas.drawCircle(0.34f, 0.38f, 0.07f, paint)
                path.reset()
                path.moveTo(0.22f, 0.70f)
                path.lineTo(0.45f, 0.45f)
                path.lineTo(0.62f, 0.70f)
                path.close()
                canvas.drawPath(path, paint)
                path.reset()
                path.moveTo(0.50f, 0.70f)
                path.lineTo(0.66f, 0.54f)
                path.lineTo(0.78f, 0.70f)
                path.close()
                canvas.drawPath(path, paint)
            }

            Icon.ARROW_LEFT, Icon.ARROW_RIGHT -> {
                if (icon == Icon.ARROW_RIGHT) canvas.scale(-1f, 1f, 0.5f, 0.5f)
                strokeStyle(0.10f)
                path.reset()
                path.moveTo(0.76f, 0.50f)
                path.lineTo(0.26f, 0.50f)
                path.moveTo(0.48f, 0.28f)
                path.lineTo(0.26f, 0.50f)
                path.lineTo(0.48f, 0.72f)
                canvas.drawPath(path, paint)
            }

            Icon.CLOSE -> {
                strokeStyle(0.10f)
                canvas.drawLine(0.30f, 0.30f, 0.70f, 0.70f, paint)
                canvas.drawLine(0.70f, 0.30f, 0.30f, 0.70f, paint)
            }

            Icon.GEAR -> {
                fillStyle()
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(i * 45f, 0.5f, 0.5f)
                    rect.set(0.44f, 0.14f, 0.56f, 0.30f)
                    canvas.drawRoundRect(rect, 0.02f, 0.02f, paint)
                    canvas.restore()
                }
                strokeStyle(0.09f)
                canvas.drawCircle(0.5f, 0.5f, 0.2f, paint)
            }

            Icon.STOP -> {
                fillStyle()
                rect.set(0.32f, 0.32f, 0.68f, 0.68f)
                canvas.drawRoundRect(rect, 0.06f, 0.06f, paint)
            }

            Icon.PLAY -> {
                fillStyle()
                path.reset()
                path.moveTo(0.40f, 0.28f)
                path.lineTo(0.40f, 0.72f)
                path.lineTo(0.74f, 0.50f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }
}
