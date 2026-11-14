package com.superrouter.agentx

import android.graphics.*
import android.graphics.drawable.Drawable

class AgentIcon(private val type: String, private val color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@AgentIcon.color
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (type == "lightning") {
            val path = Path()
            path.moveTo(w * 0.6f, 0f)
            path.lineTo(w * 0.2f, h * 0.5f)
            path.lineTo(w * 0.5f, h * 0.5f)
            path.lineTo(w * 0.4f, h)
            path.lineTo(w * 0.8f, h * 0.4f)
            path.lineTo(w * 0.5f, h * 0.4f)
            path.close()
            canvas.drawPath(path, paint)
        } else if (type == "gear") {
            val cx = w / 2
            val cy = h / 2
            val r = w * 0.35f
            canvas.drawCircle(cx, cy, r, paint)
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45).toDouble())
                canvas.drawLine(
                    cx + r * Math.cos(angle).toFloat(), cy + r * Math.sin(angle).toFloat(),
                    cx + (r + w * 0.15f) * Math.cos(angle).toFloat(), cy + (r + w * 0.15f) * Math.sin(angle).toFloat(),
                    paint
                )
            }
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class GlassDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    init {
        paint.shader = LinearGradient(
            0f, 0f, 1000f, 1000f,
            intArrayOf(Color.parseColor("#33FFFFFF"), Color.parseColor("#0AFFFFFF"), Color.parseColor("#11FFFFFF")),
            null, Shader.TileMode.CLAMP
        )
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 4f
        strokePaint.shader = LinearGradient(
            0f, 0f, 1000f, 1000f,
            intArrayOf(Color.parseColor("#CCFFFFFF"), Color.parseColor("#00FFFFFF"), Color.parseColor("#50FFFFFF")),
            null, Shader.TileMode.CLAMP
        )
    }
    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val radius = bounds.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, paint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; strokePaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; strokePaint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}