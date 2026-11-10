package com.pandortools.notecreate.ink

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import com.pandortools.notecreate.data.ImageItem
import com.pandortools.notecreate.data.Stroke
import kotlin.math.floor

/** Вспомогательная геометрия: ластик и лассо. */
object Geometry {

    fun circleHitsStroke(s: Stroke, cx: Float, cy: Float, r: Float): Boolean {
        val b = s.bounds
        if (cx + r < b.left || cx - r > b.right || cy + r < b.top || cy - r > b.bottom) return false
        val rr = r + s.width / 2f
        val r2 = rr * rr
        for (i in 0 until s.size) {
            val dx = s.x(i) - cx
            val dy = s.y(i) - cy
            if (dx * dx + dy * dy <= r2) return true
        }
        return false
    }

    /** Ластик «область»: удаляет точки внутри круга, разбивая штрих на куски. Null — штрих не задет. */
    fun eraseCircle(s: Stroke, cx: Float, cy: Float, r: Float): List<Stroke>? {
        if (!circleHitsStroke(s, cx, cy, r)) return null
        val rr = r + s.width / 2f
        val r2 = rr * rr
        val out = ArrayList<Stroke>()
        var start = -1
        for (i in 0 until s.size) {
            val dx = s.x(i) - cx
            val dy = s.y(i) - cy
            val inside = dx * dx + dy * dy <= r2
            if (!inside && start < 0) start = i
            if (inside && start >= 0) {
                if (i - start >= 2) out += s.slice(start, i)
                start = -1
            }
        }
        if (start >= 0 && s.size - start >= 2) out += s.slice(start, s.size)
        return out
    }

    fun lassoRegion(points: List<Float>): Region? {
        if (points.size < 6) return null
        val path = Path()
        path.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) { path.lineTo(points[i], points[i + 1]); i += 2 }
        path.close()
        val b = RectF()
        @Suppress("DEPRECATION")
        path.computeBounds(b, true)
        val clip = Region(Rect(floor(b.left).toInt() - 1, floor(b.top).toInt() - 1, b.right.toInt() + 2, b.bottom.toInt() + 2))
        val region = Region()
        region.setPath(path, clip)
        return region
    }

    fun strokeInRegion(s: Stroke, region: Region): Boolean {
        val n = s.size
        if (n == 0) return false
        val step = maxOf(1, n / 40)
        var inside = 0
        var total = 0
        var i = 0
        while (i < n) {
            total++
            if (region.contains(s.x(i).toInt(), s.y(i).toInt())) inside++
            i += step
        }
        return inside * 2 >= total
    }

    fun imageInRegion(im: ImageItem, region: Region) = region.contains(im.centerX.toInt(), im.centerY.toInt())

    fun imageContains(im: ImageItem, x: Float, y: Float): Boolean {
        if (im.rotation == 0f) return im.rect.contains(x, y)
        // Поворачиваем точку обратно вокруг центра.
        val a = Math.toRadians(-im.rotation.toDouble())
        val dx = x - im.centerX
        val dy = y - im.centerY
        val rx = (dx * Math.cos(a) - dy * Math.sin(a)).toFloat() + im.centerX
        val ry = (dx * Math.sin(a) + dy * Math.cos(a)).toFloat() + im.centerY
        return im.rect.contains(rx, ry)
    }
}
