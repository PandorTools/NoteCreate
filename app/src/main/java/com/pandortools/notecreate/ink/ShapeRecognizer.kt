package com.pandortools.notecreate.ink

import com.pandortools.notecreate.data.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Converts a paused freehand line or loop into clean geometry when the fit is clear. */
object ShapeRecognizer {
    fun recognize(stroke: Stroke): Stroke? {
        if (stroke.size < 7) return null
        val firstX = stroke.x(0); val firstY = stroke.y(0)
        val lastX = stroke.x(stroke.size - 1); val lastY = stroke.y(stroke.size - 1)
        val chord = hypot(lastX - firstX, lastY - firstY).coerceAtLeast(0.001f)
        var pathLength = 0f
        for (i in 1 until stroke.size) pathLength += hypot(stroke.x(i) - stroke.x(i - 1), stroke.y(i) - stroke.y(i - 1))

        val left = (0 until stroke.size).minOf { stroke.x(it) }
        val right = (0 until stroke.size).maxOf { stroke.x(it) }
        val top = (0 until stroke.size).minOf { stroke.y(it) }
        val bottom = (0 until stroke.size).maxOf { stroke.y(it) }
        val width = right - left; val height = bottom - top
        val diagonal = hypot(width, height).coerceAtLeast(0.001f)

        // Closed, fairly even loop: approximate an ellipse using its observed bounds.
        if (stroke.size >= 12 && hypot(firstX - lastX, firstY - lastY) < diagonal * 0.32f &&
            width > 8f && height > 8f && max(width, height) / minOf(width, height) < 2.4f) {
            val cx = (left + right) / 2f; val cy = (top + bottom) / 2f
            val rx = width / 2f; val ry = height / 2f
            var radialError = 0f
            for (i in 0 until stroke.size) {
                val nx = (stroke.x(i) - cx) / rx
                val ny = (stroke.y(i) - cy) / ry
                radialError += abs(hypot(nx, ny) - 1f)
            }
            if (radialError / stroke.size < 0.34f) {
                val count = 64
                val pts = FloatArray(count * Stroke.STRIDE)
                for (i in 0 until count) {
                    val a = (2.0 * Math.PI * i / count).toFloat()
                    val o = i * Stroke.STRIDE
                    pts[o] = cx + rx * kotlin.math.cos(a); pts[o + 1] = cy + ry * kotlin.math.sin(a)
                    pts[o + 2] = stroke.pressure(stroke.size / 2); pts[o + 3] = 0f
                }
                return Stroke(stroke.pen, stroke.color, stroke.width, pts)
            }
        }

        // Straight strokes, including diagonal lines.
        if (pathLength / chord < 1.16f) {
            var maxError = 0f
            for (i in 1 until stroke.size - 1) {
                val distance = abs((lastY - firstY) * stroke.x(i) - (lastX - firstX) * stroke.y(i) +
                    lastX * firstY - lastY * firstX) / chord
                maxError = max(maxError, distance)
            }
            if (maxError / diagonal < 0.075f) {
                val pts = floatArrayOf(firstX, firstY, stroke.pressure(0), stroke.tilt(0),
                    lastX, lastY, stroke.pressure(stroke.size - 1), stroke.tilt(stroke.size - 1))
                return Stroke(stroke.pen, stroke.color, stroke.width, pts)
            }
        }

        // One continuous arrow gesture: shaft, first wing toward the tip, then the second wing.
        if (stroke.size >= 14 && pathLength / chord > 1.2f) {
            val start = (stroke.size * 0.42f).toInt().coerceAtLeast(2)
            val end = (stroke.size * 0.82f).toInt().coerceAtMost(stroke.size - 3)
            for (tipA in start until end) {
                for (tipB in (tipA + 2)..end) {
                    val tx = (stroke.x(tipA) + stroke.x(tipB)) / 2f
                    val ty = (stroke.y(tipA) + stroke.y(tipB)) / 2f
                    if (hypot(stroke.x(tipA) - stroke.x(tipB), stroke.y(tipA) - stroke.y(tipB)) > diagonal * 0.12f) continue
                    val w1x = stroke.x(tipA + 1) - tx; val w1y = stroke.y(tipA + 1) - ty
                    val w2x = stroke.x(tipB + 1) - tx; val w2y = stroke.y(tipB + 1) - ty
                    val l1 = hypot(w1x, w1y); val l2 = hypot(w2x, w2y)
                    if (l1 < diagonal * 0.045f || l2 < diagonal * 0.045f || l1 > diagonal * 0.32f || l2 > diagonal * 0.32f) continue
                    val cosine = ((w1x * w2x + w1y * w2y) / (l1 * l2)).coerceIn(-1f, 1f)
                    if (cosine > 0.78f) continue
                    val pts = floatArrayOf(
                        firstX, firstY, stroke.pressure(0), 0f,
                        tx, ty, stroke.pressure(tipA), 0f,
                        tx + w1x, ty + w1y, stroke.pressure(tipA + 1), 0f,
                        tx, ty, stroke.pressure(tipB), 0f,
                        tx + w2x, ty + w2y, stroke.pressure(tipB + 1), 0f,
                    )
                    return Stroke(stroke.pen, stroke.color, stroke.width, pts)
                }
            }
        }
        return null
    }
}
