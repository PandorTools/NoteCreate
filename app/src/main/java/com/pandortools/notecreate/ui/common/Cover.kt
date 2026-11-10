package com.pandortools.notecreate.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandortools.notecreate.data.CoverMaterial
import com.pandortools.notecreate.ui.theme.Accent
import com.pandortools.notecreate.ui.theme.Cream
import com.pandortools.notecreate.ui.theme.Ink
import com.pandortools.notecreate.ui.theme.PagesBeige
import kotlin.random.Random

/** Обложка тетради, нарисованная кодом: материал, корешок, резинка, ленточка, наклейка с названием. */
@Composable
fun NotebookCover(
    title: String,
    color: Int,
    material: CoverMaterial,
    elastic: Boolean,
    ribbon: Boolean,
    modifier: Modifier = Modifier,
    seed: Int = title.hashCode(),
    showPages: Boolean = true,
) {
    val base = Color(color)
    val dots = remember(seed, material) {
        val r = Random(seed)
        List(220) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    BoxWithConstraints(modifier) {
        val labelWidth = maxWidth * 0.62f
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(w * 0.05f)
            val pageInset = if (showPages) w * 0.035f else 0f
            if (showPages) {
                drawRoundRect(PagesBeige, Offset(w * 0.1f, h * 0.012f), Size(w * 0.9f, h * 0.976f), corner)
            }
            val coverW = w - pageInset
            drawRoundRect(base, Offset.Zero, Size(coverW, h), corner)
            drawTexture(material, base, coverW, h, dots)
            // корешок
            val spineW = coverW * 0.1f
            val spine = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, spineW, h, corner, CornerRadius.Zero, CornerRadius.Zero, corner))
            }
            drawPath(spine, lerp(base, Color.Black, 0.22f))
            drawLine(Color.Black.copy(alpha = 0.18f), Offset(spineW, 0f), Offset(spineW, h), strokeWidth = 1.5f)
            // резинка
            if (elastic) {
                val ex = coverW * 0.85f
                drawRect(lerp(base, Color.Black, 0.35f), Offset(ex, 0f), Size(coverW * 0.04f, h))
                drawRect(Color.White.copy(alpha = 0.10f), Offset(ex, 0f), Size(coverW * 0.012f, h))
            }
            // ленточка-закладка
            if (ribbon) {
                val rx = coverW * 0.26f
                val rw = coverW * 0.06f
                val path = Path().apply {
                    moveTo(rx, h * 0.93f)
                    lineTo(rx + rw, h * 0.93f)
                    lineTo(rx + rw, h * 1.0f)
                    lineTo(rx + rw / 2f, h * 0.975f)
                    lineTo(rx, h * 1.0f)
                    close()
                }
                drawPath(path, Accent)
            }
        }
        if (title.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = maxHeight * 0.2f, start = maxWidth * 0.06f)
                    .fillMaxWidth(0.62f)
                    .background(Cream, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                val fs = (labelWidth.value / 9f).coerceIn(9f, 22f)
                Text(
                    title,
                    color = Ink,
                    fontSize = fs.sp,
                    lineHeight = (fs * 1.15f).sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun DrawScope.drawTexture(
    material: CoverMaterial,
    @Suppress("UNUSED_PARAMETER") base: Color,
    w: Float,
    h: Float,
    dots: List<Triple<Float, Float, Float>>,
) {
    when (material) {
        CoverMaterial.LEATHER -> {
            dots.forEach { (x, y, a) ->
                drawCircle(Color.Black.copy(alpha = 0.05f + a * 0.06f), radius = 1f + a * 2.2f, center = Offset(x * w, y * h))
            }
            val inset = w * 0.06f
            drawRoundRect(
                Color.White.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.1f + inset * 0.6f, inset),
                size = Size(w - w * 0.1f - inset * 1.6f, h - inset * 2),
                cornerRadius = CornerRadius(w * 0.03f),
                style = Stroke(width = 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))),
            )
        }
        CoverMaterial.FABRIC -> {
            val step = w / 46f
            var x = -h
            while (x < w) {
                drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x + h, h), strokeWidth = 1f)
                drawLine(Color.Black.copy(alpha = 0.07f), Offset(x + h, 0f), Offset(x, h), strokeWidth = 1f)
                x += step
            }
        }
        CoverMaterial.KRAFT -> {
            dots.forEach { (x, y, a) ->
                val c = if (a > 0.5f) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.12f)
                drawCircle(c, radius = 0.8f + a * 1.4f, center = Offset(x * w, y * h))
            }
        }
        CoverMaterial.PLAIN -> {
            drawRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.08f)),
                    start = Offset.Zero, end = Offset(w, h),
                ),
                size = Size(w, h),
            )
        }
    }
    // лёгкий блик сверху
    drawRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), endY = h * 0.3f),
        size = Size(w, h * 0.3f),
    )
}

/** Логотип приложения (обложка с пером) для заголовков. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 108f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        drawRoundRect(Cream, Offset.Zero, Size(108 * s, 108 * s), CornerRadius(24 * s))
        val k = 1.45f
        fun t(x: Float, y: Float) = o(53 + (x - 55.25f) * k, 54 + (y - 54f) * k)
        fun sz(w: Float, h: Float) = Size(w * k * s, h * k * s)
        drawRoundRect(PagesBeige, t(68.5f, 30.5f), sz(8f, 47f), CornerRadius(2.5f * k * s))
        drawRoundRect(Ink, t(34f, 28f), sz(40f, 52f), CornerRadius(4f * k * s))
        drawRoundRect(Accent, t(34f, 28f), sz(8f, 52f), CornerRadius(4f * k * s))
        drawRect(Accent, t(38f, 28f), sz(4f, 52f))
        drawRect(Cream.copy(alpha = 0.28f), t(66f, 28f), sz(2.6f, 52f))
        val nib = Path().apply {
            moveTo(t(54f, 39f).x, t(54f, 39f).y)
            cubicTo(t(58.5f, 43f).x, t(58.5f, 43f).y, t(61f, 47.5f).x, t(61f, 47.5f).y, t(61f, 51f).x, t(61f, 51f).y)
            lineTo(t(54f, 66f).x, t(54f, 66f).y)
            lineTo(t(47f, 51f).x, t(47f, 51f).y)
            cubicTo(t(47f, 47.5f).x, t(47f, 47.5f).y, t(49.5f, 43f).x, t(49.5f, 43f).y, t(54f, 39f).x, t(54f, 39f).y)
            close()
        }
        drawPath(nib, Cream)
        drawCircle(Ink, 2f * k * s, t(54f, 52f))
        drawRect(Ink, t(53.4f, 53.5f), sz(1.2f, 10.5f))
    }
}
