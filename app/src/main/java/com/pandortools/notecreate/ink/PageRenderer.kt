package com.pandortools.notecreate.ink

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
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
import com.pandortools.notecreate.data.PageLayers
import com.pandortools.notecreate.data.PageTemplate
import com.pandortools.notecreate.data.TextBlock
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
    private val watercolorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
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

    fun drawText(canvas: Canvas, item: TextBlock) {
        val oldType = textPaint.typeface
        val oldSize = textPaint.textSize
        val oldColor = textPaint.color
        textPaint.typeface = when (item.fontFamily.lowercase()) {
            "serif" -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            "mono" -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            else -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        textPaint.textSize = item.fontSize.coerceIn(6f, 144f)
        textPaint.color = item.color
        val lineHeight = textPaint.fontSpacing
        item.text.split('\n').forEachIndexed { index, line ->
            canvas.drawText(line, item.x, item.y + index * lineHeight, textPaint)
        }
        textPaint.typeface = oldType; textPaint.textSize = oldSize; textPaint.color = oldColor
    }

    fun drawTemplate(canvas: Canvas, page: PageData, nb: Notebook) {
        val w = nb.pageWidth; val h = nb.pageHeight; val margin = 14f * MM_TO_PT
        val dark = isDark(nb.paperColor)
        val ink = if (dark) Color.argb(170, 255, 255, 255) else Color.argb(185, 37, 64, 91)
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.color = ink; textPaint.textSize = 10f
        linePaint.color = if (dark) Color.argb(75, 255, 255, 255) else Color.argb(75, 46, 76, 107)
        linePaint.strokeWidth = 0.8f
        when (page.template) {
            PageTemplate.NONE -> Unit
            PageTemplate.HABITS -> {
                canvas.drawText("ТРЕКЕР ПРИВЫЧЕК", margin, margin + 12f, textPaint)
                val top = margin + 28f; val row = 28f; val col = (w - margin * 2) / 8f
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { i, label ->
                    canvas.drawText(label, margin + (i + 1) * col + 3f, top - 5f, textPaint)
                }
                for (r in 0..9) canvas.drawLine(margin, top + r * row, w - margin, top + r * row, linePaint)
                for (c in 0..8) canvas.drawLine(margin + c * col, top, margin + c * col, top + row * 9, linePaint)
                textPaint.textSize = 8f
                for (r in 0 until 9) canvas.drawText("Привычка ${r + 1}", margin + 3f, top + row * (r + 0.66f), textPaint)
            }
            PageTemplate.WEEK -> {
                canvas.drawText("ПЛАН НА НЕДЕЛЮ", margin, margin + 12f, textPaint)
                val days = listOf("ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ")
                val row = (h - margin * 2 - 26f) / days.size
                days.forEachIndexed { i, day ->
                    val y = margin + 32f + i * row
                    textPaint.textSize = 8f; canvas.drawText(day, margin + 3f, y + 10f, textPaint)
                    canvas.drawLine(margin, y + 16f, w - margin, y + 16f, linePaint)
                    canvas.drawLine(margin, y + row - 2f, w - margin, y + row - 2f, linePaint)
                }
            }
            PageTemplate.TODO -> {
                canvas.drawText("СПИСОК ДЕЛ", margin, margin + 12f, textPaint)
                var y = margin + 38f
                while (y < h - margin) {
                    canvas.drawRect(margin, y - 9f, margin + 10f, y + 1f, linePaint)
                    canvas.drawLine(margin + 18f, y, w - margin, y, linePaint)
                    y += 28f
                }
            }
            PageTemplate.DAY_REVIEW -> {
                canvas.drawText("ИТОГИ ДНЯ", margin, margin + 12f, textPaint)
                val prompts = listOf("Лучший момент дня", "Что получилось хорошо", "Что хочу улучшить", "За что благодарен")
                val section = (h - margin * 2 - 28f) / prompts.size
                prompts.forEachIndexed { i, prompt ->
                    val y = margin + 32f + i * section
                    textPaint.textSize = 9f; canvas.drawText(prompt, margin, y, textPaint)
                    for (line in 1..3) canvas.drawLine(margin, y + line * 22f, w - margin, y + line * 22f, linePaint)
                }
            }
        }
    }

    // ---------- Штрихи ----------

    fun drawStroke(canvas: Canvas, s: Stroke) {
        val n = s.size
        if (n == 0) return
        when (s.pen) {
            PenType.HIGHLIGHTER -> drawHighlighter(canvas, s)
            PenType.WATERCOLOR -> drawWatercolor(canvas, s)
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
            PenType.WATERCOLOR -> s.width * (0.65f + 0.85f * p) * (1f + 0.5f * s.tilt(i))
            PenType.INK -> s.width * (0.28f + 1.25f * p) * (1f + 0.35f * s.tilt(i))
            PenType.TEXTURED -> s.width * (0.7f + 0.55f * p) * (1f + 1.8f * s.tilt(i))
        }
        return if (prev <= 0f) w else prev * 0.55f + w * 0.45f
    }

    private fun drawVariable(canvas: Canvas, s: Stroke) {
        val paint = segPaint
        paint.color = s.color
        paint.shader = null
        val pencil = s.pen == PenType.PENCIL || s.pen == PenType.TEXTURED
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

    /** Watercolour-style soft underwash with a defined pigment center. */
    private fun drawWatercolor(canvas: Canvas, s: Stroke) {
        if (s.size == 1) {
            fillPaint.color = Color.argb((Color.alpha(s.color) * 0.28f).toInt(), Color.red(s.color), Color.green(s.color), Color.blue(s.color))
            canvas.drawCircle(s.x(0), s.y(0), s.width, fillPaint)
            return
        }
        watercolorPaint.color = s.color
        watercolorPaint.alpha = (Color.alpha(s.color) * 0.14f).toInt()
        watercolorPaint.strokeWidth = s.width * 2.4f
        watercolorPaint.maskFilter = BlurMaskFilter(s.width * 0.55f, BlurMaskFilter.Blur.NORMAL)
        for (i in 1 until s.size) canvas.drawLine(s.x(i - 1), s.y(i - 1), s.x(i), s.y(i), watercolorPaint)
        watercolorPaint.maskFilter = null
        watercolorPaint.alpha = (Color.alpha(s.color) * 0.34f).toInt()
        for (i in 1 until s.size) {
            watercolorPaint.strokeWidth = s.width * (0.7f + 0.8f * s.pressure(i).coerceIn(0f, 1f))
            canvas.drawLine(s.x(i - 1), s.y(i - 1), s.x(i), s.y(i), watercolorPaint)
        }
        watercolorPaint.alpha = 255
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
        // Старые штрихи хранят непрозрачный цвет — для них прозрачность маркера 38%.
        // Новые уже несут выбранную прозрачность в альфе цвета, второй раз её не умножаем.
        val a = Color.alpha(s.color)
        pathPaint.alpha = if (a >= 255) (255 * 0.38f).toInt() else a
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
        if (page.layer(PageLayers.BACKGROUND)?.visible != false) {
            drawPaper(canvas, nb, date)
            drawTemplate(canvas, page, nb)
        }
        for (im in page.images) if (page.layer(im.layerId)?.visible != false) drawImage(canvas, im, images(im))
        // Маркер рисуем первым, чтобы он лежал под текстом.
        for (s in page.strokes) if (page.layer(s.layerId)?.visible != false && s.pen == PenType.HIGHLIGHTER) drawStroke(canvas, s)
        for (s in page.strokes) if (page.layer(s.layerId)?.visible != false && s.pen != PenType.HIGHLIGHTER) drawStroke(canvas, s)
        for (text in page.textBlocks) if (page.layer(text.layerId)?.visible != false) drawText(canvas, text)
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
