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

        // Closed polygonal gestures: fit four corners for rectangles/squares and
        // three corners for triangles. The user can draw it roughly; the result
        // is normalized to clean, straight edges after the hold delay.
        if (stroke.size >= 8 && hypot(firstX - lastX, firstY - lastY) < diagonal * 0.38f &&
            width > 10f && height > 10f) {
            val corners = listOf(
                floatArrayOf(left, top), floatArrayOf(right, top),
                floatArrayOf(right, bottom), floatArrayOf(left, bottom),
            )
            val cornerErrors = corners.map { corner ->
                (0 until stroke.size).minOf { i -> hypot(stroke.x(i) - corner[0], stroke.y(i) - corner[1]) }
            }
            val rectangleError = cornerErrors.maxOrNull()!! / diagonal
            val aspect = max(width, height) / minOf(width, height)
            if (rectangleError < 0.13f && aspect < 3.2f) {
                val pts = floatArrayOf(
                    left, top, stroke.pressure(0), 0f,
                    right, top, stroke.pressure(stroke.size / 4), 0f,
                    right, bottom, stroke.pressure(stroke.size / 2), 0f,
                    left, bottom, stroke.pressure(stroke.size * 3 / 4), 0f,
                    left, top, stroke.pressure(stroke.size - 1), 0f,
                )
                return Stroke(stroke.pen, stroke.color, stroke.width, pts)
            }

            // For a triangle, locate the three most separated clusters along
            // the gesture. Require an actually triangular path to avoid turning
            // an irregular oval into a triangle.
            val sampleCount = stroke.size.coerceAtMost(24)
            val samples = (0 until sampleCount).map { j ->
                val i = j * (stroke.size - 1) / (sampleCount - 1)
                floatArrayOf(stroke.x(i), stroke.y(i))
            }
            var best: List<FloatArray>? = null
            var bestError = Float.MAX_VALUE
            for (a in samples.indices) for (b in a + 1 until samples.size) for (c in b + 1 until samples.size) {
                val vertices = listOf(samples[a], samples[b], samples[c])
                val error = samples.sumOf { p ->
                    val a0 = vertices[0]; val a1 = vertices[1]; val a2 = vertices[2]
                    minOf(
                        pointSegmentDistance(p[0], p[1], a0[0], a0[1], a1[0], a1[1]),
                        pointSegmentDistance(p[0], p[1], a1[0], a1[1], a2[0], a2[1]),
                        pointSegmentDistance(p[0], p[1], a2[0], a2[1], a0[0], a0[1]),
                    ).toDouble()
                }.toFloat() / samples.size / diagonal
                if (error < bestError) { bestError = error; best = vertices }
            }
            if (bestError < 0.045f) {
                val vertices = best!!
                val ordered = vertices.sortedBy { kotlin.math.atan2((it[1] - (top + bottom) / 2f).toDouble(), (it[0] - (left + right) / 2f).toDouble()) }
                val pts = FloatArray(4 * Stroke.STRIDE)
                for (i in 0..3) {
                    val v = ordered[i % 3]; val o = i * Stroke.STRIDE
                    pts[o] = v[0]; pts[o + 1] = v[1]; pts[o + 2] = stroke.pressure(i * (stroke.size - 1) / 3); pts[o + 3] = 0f
                }
                return Stroke(stroke.pen, stroke.color, stroke.width, pts)
            }
        }

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

    private fun pointSegmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 <= 0.0001f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
