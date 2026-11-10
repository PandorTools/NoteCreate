package com.pandortools.notecreate.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.hypot

/**
 * Эффект перелистывания: страница сгибается по линии,
 * перпендикулярной отрезку «угол — палец», и показывает обратную сторону.
 */
class PageCurl {

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val m = Matrix()

    /**
     * @param page прямоугольник страницы на экране
     * @param under страница, которая открывается (снизу)
     * @param top страница, которая переворачивается
     * @param corner угол страницы, который тянут
     * @param touch где сейчас этот угол
     * @param backColor цвет оборота бумаги
     */
    fun draw(
        canvas: Canvas,
        page: RectF,
        under: Bitmap,
        top: Bitmap,
        corner: PointF,
        touch: PointF,
        backColor: Int,
    ) {
        val dx = corner.x - touch.x
        val dy = corner.y - touch.y
        val len = hypot(dx, dy)
        if (len < 1f) {
            canvas.drawBitmap(top, 0f, 0f, bmpPaint)
            return
        }
        val nx = dx / len
        val ny = dy / len
        val mx = (corner.x + touch.x) / 2f
        val my = (corner.y + touch.y) / 2f
        val rect = listOf(
            PointF(page.left, page.top), PointF(page.right, page.top),
            PointF(page.right, page.bottom), PointF(page.left, page.bottom),
        )
        // >0 — сторона угла (отогнутая часть)
        val side: (PointF) -> Float = { p -> (p.x - mx) * nx + (p.y - my) * ny }
        val remaining = clip(rect) { -side(it) }
        val flipped = clip(rect, side)

        // 1. Нижняя страница
        canvas.save()
        canvas.clipRect(page)
        canvas.drawBitmap(under, 0f, 0f, bmpPaint)
        // тень от сгиба на открывшейся странице
        if (flipped.size >= 3) {
            canvas.save()
            canvas.clipPath(toPath(flipped))
            val sw = (len * 0.35f).coerceIn(12f, 60f)
            shade.shader = LinearGradient(
                mx, my, mx + nx * sw, my + ny * sw,
                Color.argb(110, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(page, shade)
            canvas.restore()
        }
        canvas.restore()

        // 2. Оставшаяся часть верхней страницы
        if (remaining.size >= 3) {
            canvas.save()
            canvas.clipPath(toPath(remaining))
            canvas.drawBitmap(top, 0f, 0f, bmpPaint)
            canvas.restore()
        }

        // 3. Оборот отогнутой части — зеркальное отражение через линию сгиба
        if (flipped.size >= 3) {
            val ddx = -ny
            val ddy = nx
            val r00 = ddx * ddx - ddy * ddy
            val r01 = 2 * ddx * ddy
            val r11 = ddy * ddy - ddx * ddx
            val tx = mx - (r00 * mx + r01 * my)
            val ty = my - (r01 * mx + r11 * my)
            m.setValues(floatArrayOf(r00, r01, tx, r01, r11, ty, 0f, 0f, 1f))
            val reflected = flipped.map { p ->
                val a = floatArrayOf(p.x, p.y); m.mapPoints(a); PointF(a[0], a[1])
            }
            val back = toPath(reflected)
            canvas.save()
            canvas.clipPath(back)
            canvas.save()
            canvas.concat(m)
            canvas.drawBitmap(top, 0f, 0f, bmpPaint)
            canvas.restore()
            fill.color = backColor
            fill.alpha = 225
            canvas.drawPath(back, fill)
            val sw = (len * 0.5f).coerceIn(16f, 120f)
            shade.shader = LinearGradient(
                mx, my, mx - nx * sw, my - ny * sw,
                Color.argb(70, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            canvas.drawPath(back, shade)
            canvas.restore()
            // контур края оборота
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 1f
            fill.color = Color.argb(60, 0, 0, 0)
            canvas.drawPath(back, fill)
            fill.style = Paint.Style.FILL
        }
        shade.shader = null
    }

    private fun toPath(poly: List<PointF>): Path {
        val p = Path()
        p.moveTo(poly[0].x, poly[0].y)
        for (i in 1 until poly.size) p.lineTo(poly[i].x, poly[i].y)
        p.close()
        return p
    }

    /** Отсечение многоугольника полуплоскостью f(p) >= 0 (алгоритм Сазерленда — Ходжмана). */
    private fun clip(poly: List<PointF>, f: (PointF) -> Float): List<PointF> {
        val out = ArrayList<PointF>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            val fa = f(a)
            val fb = f(b)
            if (fa >= 0) out += a
            if ((fa >= 0) != (fb >= 0)) {
                val t = fa / (fa - fb)
                out += PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        return out
    }
}
