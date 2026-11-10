package com.pandortools.notecreate.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.pandortools.notecreate.data.ImageItem
import com.pandortools.notecreate.data.MM_TO_PT
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.data.NotebookTemplate
import com.pandortools.notecreate.data.PageData
import com.pandortools.notecreate.data.PaperType
import com.pandortools.notecreate.data.PenType
import com.pandortools.notecreate.data.Stroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.random.Random

/**
 * Рисует бумагу, картинки и штрихи. Все координаты — в пунктах страницы.
 * Экземпляр не потокобезопасен: для UI — [PageRenderer.ui], для фона создавайте свой.
 */
class PageRenderer {

    companion object {
        /** Рендерер для главного потока. */
        val ui by lazy { PageRenderer() }

        fun formatDate(time: Long): String =
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru")).format(Date(time))

        fun isDark(color: Int): Boolean {
            val l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
            return l < 110
        }
    }

    private val segPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }
    private val tmpPath = Path()

    /** Текстура зерна для карандаша. */
    private val grainShader: BitmapShader by lazy {
        val size = 96
        val rnd = Random(7)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val px = IntArray(size * size) { Color.argb(110 + rnd.nextInt(146), 0, 0, 0) }
        // ALPHA_8 берёт только альфу, поэтому заполняем через ARGB-битмап и копируем.
        val argb = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawBitmap(argb, 0f, 0f, null)
        argb.recycle()
        BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    // ---------- Бумага ----------

    fun drawPaper(canvas: Canvas, nb: Notebook, date: Long?) {
        val w = nb.pageWidth
        val h = nb.pageHeight
        fillPaint.color = nb.paperColor
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        val dark = isDark(nb.paperColor)
        val lineColor = if (dark) Color.argb(55, 255, 255, 255) else Color.argb(70, 70, 110, 160)
        linePaint.color = lineColor
        linePaint.strokeWidth = 0.6f
        linePaint.style = Paint.Style.STROKE
        val mm = MM_TO_PT

        when (nb.paperType) {
            PaperType.BLANK -> Unit
            PaperType.LINED, PaperType.LINED_WIDE -> {
                val step = (if (nb.paperType == PaperType.LINED) 8f else 10f) * mm
                var y = 22f * mm
                while (y < h - 8f * mm) { canvas.drawLine(0f, y, w, y, linePaint); y += step }
                linePaint.color = if (dark) Color.argb(70, 255, 140, 140) else Color.argb(90, 220, 80, 80)
                canvas.drawLine(18f * mm, 0f, 18f * mm, h, linePaint)
            }
            PaperType.GRID -> {
                val step = 5f * mm
                var x = step
                while (x < w) { canvas.drawLine(x, 0f, x, h, linePaint); x += step }
                var y = step
                while (y < h) { canvas.drawLine(0f, y, w, y, linePaint); y += step }
            }
            PaperType.DOTS -> {
                val step = 5f * mm
                fillPaint.color = if (dark) Color.argb(90, 255, 255, 255) else Color.argb(110, 70, 90, 120)
                var y = step
                while (y < h) {
                    var x = step
                    while (x < w) { canvas.drawCircle(x, y, 0.7f, fillPaint); x += step }
                    y += step
                }
            }
            PaperType.MUSIC -> {
                val line = 2f * mm
                var top = 25f * mm
                while (top + 4 * line < h - 10f * mm) {
                    for (i in 0..4) canvas.drawLine(12f * mm, top + i * line, w - 12f * mm, top + i * line, linePaint)
                    top += 4 * line + 12f * mm
                }
            }
        }

        if (nb.template == NotebookTemplate.DIARY && date != null) {
            textPaint.color = if (dark) Color.argb(200, 255, 255, 255) else Color.argb(200, 30, 42, 68)
            textPaint.textSize = 11f
            canvas.drawText(formatDate(date), 22f * mm, 14f * mm, textPaint)
        }
    }

    // ---------- Картинки ----------

    fun drawImage(canvas: Canvas, item: ImageItem, bitmap: Bitmap?) {
        canvas.save()
        if (item.rotation != 0f) canvas.rotate(item.rotation, item.centerX, item.centerY)
        val r = item.rect
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, r, bitmapPaint)
        } else {
            fillPaint.color = Color.argb(40, 120, 120, 120)
            canvas.drawRect(r, fillPaint)
        }
        canvas.restore()
    }

    // ---------- Штрихи ----------

    fun drawStroke(canvas: Canvas, s: Stroke) {
        val n = s.size
        if (n == 0) return
        when (s.pen) {
            PenType.HIGHLIGHTER -> drawHighlighter(canvas, s)
            else -> drawVariable(canvas, s)
        }
    }

    /** Ширина пера в точке i (для перьев с переменной толщиной). */
    private fun widthAt(s: Stroke, i: Int, prev: Float): Float {
        val p = s.pressure(i).coerceIn(0f, 1f)
        val w = when (s.pen) {
            PenType.BALLPOINT -> s.width * (0.75f + 0.45f * p)
            PenType.FOUNTAIN -> {
                val j = if (i > 0) i - 1 else 0
                val ang = atan2((s.y(i) - s.y(j)).toDouble(), (s.x(i) - s.x(j)).toDouble())
                val dir = 0.45f + 0.55f * abs(sin(ang - PI / 4)).toFloat()
                s.width * (0.25f + 1.05f * p) * dir
            }
            PenType.PENCIL -> s.width * (0.8f + 0.4f * p) * (1f + 2.2f * s.tilt(i))
            PenType.HIGHLIGHTER -> s.width
        }
        return if (prev <= 0f) w else prev * 0.55f + w * 0.45f
    }

    private fun drawVariable(canvas: Canvas, s: Stroke) {
        val paint = segPaint
        paint.color = s.color
        paint.shader = null
        val pencil = s.pen == PenType.PENCIL
        if (pencil) paint.shader = grainShader
        var w = -1f
        val n = s.size
        if (n == 1) {
            w = widthAt(s, 0, -1f)
            fillPaint.color = s.color
            canvas.drawCircle(s.x(0), s.y(0), w / 2f, fillPaint)
            return
        }
        for (i in 1 until n) {
            w = widthAt(s, i, w)
            if (pencil) {
                val a = (95 + 150 * s.pressure(i).coerceIn(0f, 1f)).toInt()
                paint.alpha = a * Color.alpha(s.color) / 255
            }
            paint.strokeWidth = w
            canvas.drawLine(s.x(i - 1), s.y(i - 1), s.x(i), s.y(i), paint)
        }
        paint.shader = null
    }

    private fun drawHighlighter(canvas: Canvas, s: Stroke) {
        val path = tmpPath
        path.reset()
        path.moveTo(s.x(0), s.y(0))
        val n = s.size
        if (n == 1) path.lineTo(s.x(0) + 0.1f, s.y(0))
        for (i in 1 until n) {
            val mx = (s.x(i - 1) + s.x(i)) / 2f
            val my = (s.y(i - 1) + s.y(i)) / 2f
            path.quadTo(s.x(i - 1), s.y(i - 1), mx, my)
        }
        path.lineTo(s.x(n - 1), s.y(n - 1))
        pathPaint.color = s.color
        pathPaint.alpha = (Color.alpha(s.color) * 0.38f).toInt()
        pathPaint.strokeWidth = s.width
        pathPaint.strokeCap = Paint.Cap.SQUARE
        canvas.drawPath(path, pathPaint)
        pathPaint.strokeCap = Paint.Cap.ROUND
    }

    // ---------- Страница целиком ----------

    fun drawPage(
        canvas: Canvas,
        nb: Notebook,
        page: PageData,
        date: Long?,
        images: (ImageItem) -> Bitmap?,
    ) {
        drawPaper(canvas, nb, date)
        for (im in page.images) drawImage(canvas, im, images(im))
        // Маркер рисуем первым, чтобы он лежал под текстом.
        for (s in page.strokes) if (s.pen == PenType.HIGHLIGHTER) drawStroke(canvas, s)
        for (s in page.strokes) if (s.pen != PenType.HIGHLIGHTER) drawStroke(canvas, s)
    }

    /** Отрисовка страницы в битмап заданной ширины (миниатюры, PNG, перелистывание). */
    fun renderBitmap(
        nb: Notebook,
        page: PageData,
        date: Long?,
        widthPx: Int,
        images: (ImageItem) -> Bitmap?,
    ): Bitmap {
        val scale = widthPx / nb.pageWidth
        val hPx = (nb.pageHeight * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), hPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        drawPage(c, nb, page, date, images)
        return bmp
    }

    fun pageRect(nb: Notebook) = RectF(0f, 0f, nb.pageWidth, nb.pageHeight)
}
