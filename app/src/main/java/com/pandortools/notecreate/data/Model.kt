package com.pandortools.notecreate.data

import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sqrt

/** Тип пера. Порядок не менять — ordinal пишется в файл страницы. */
enum class PenType(val title: String, val defaultWidth: Float) {
    BALLPOINT("Ручка", 1.6f),
    FOUNTAIN("Перо", 2.2f),
    PENCIL("Карандаш", 1.8f),
    HIGHLIGHTER("Маркер", 10f),
}

/**
 * Штрих в координатах страницы (пункты, 1/72 дюйма).
 * points — плоский массив по [STRIDE] значений: x, y, нажим (0..1), наклон (0..1).
 */
class Stroke(
    val pen: PenType,
    val color: Int,
    val width: Float,
    val points: FloatArray,
) {
    val size: Int get() = points.size / STRIDE

    fun x(i: Int) = points[i * STRIDE]
    fun y(i: Int) = points[i * STRIDE + 1]
    fun pressure(i: Int) = points[i * STRIDE + 2]
    fun tilt(i: Int) = points[i * STRIDE + 3]

    val bounds: RectF by lazy {
        val r = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in 0 until size) {
            val x = x(i); val y = y(i)
            if (x < r.left) r.left = x
            if (y < r.top) r.top = y
            if (x > r.right) r.right = x
            if (y > r.bottom) r.bottom = y
        }
        val pad = maxWidth()
        r.inset(-pad, -pad)
        r
    }

    fun maxWidth(): Float = when (pen) {
        PenType.HIGHLIGHTER -> width
        PenType.PENCIL -> width * 3f
        else -> width * 1.5f
    }

    fun recolored(newColor: Int) = Stroke(pen, newColor, width, points)

    fun transformed(m: Matrix): Stroke {
        val n = size
        val xy = FloatArray(n * 2)
        for (i in 0 until n) { xy[i * 2] = x(i); xy[i * 2 + 1] = y(i) }
        m.mapPoints(xy)
        val out = points.copyOf()
        for (i in 0 until n) { out[i * STRIDE] = xy[i * 2]; out[i * STRIDE + 1] = xy[i * 2 + 1] }
        val v = FloatArray(9); m.getValues(v)
        val scale = sqrt(max(0.0001f, kotlin.math.abs(v[Matrix.MSCALE_X] * v[Matrix.MSCALE_Y] - v[Matrix.MSKEW_X] * v[Matrix.MSKEW_Y])))
        return Stroke(pen, color, width * scale, out)
    }

    /** Часть штриха с точками [from, to). */
    fun slice(from: Int, to: Int) = Stroke(pen, color, width, points.copyOfRange(from * STRIDE, to * STRIDE))

    companion object { const val STRIDE = 4 }
}

data class ImageItem(
    val id: String,
    val fileName: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float = 0f,
    val locked: Boolean = false,
) {
    val rect: RectF get() = RectF(x, y, x + w, y + h)
    val centerX get() = x + w / 2f
    val centerY get() = y + h / 2f
}

/** Содержимое одной страницы. Неизменяемое — каждая правка создаёт новый объект (удобно для отмены). */
data class PageData(
    val strokes: List<Stroke> = emptyList(),
    val images: List<ImageItem> = emptyList(),
) {
    val isEmpty get() = strokes.isEmpty() && images.isEmpty()
}

enum class PaperType(val title: String) {
    BLANK("Чистая"),
    LINED("Линейка"),
    LINED_WIDE("Широкая линейка"),
    GRID("Клетка"),
    DOTS("Точки"),
    MUSIC("Нотный стан"),
}

enum class PageFormat(val title: String, val widthMm: Float, val heightMm: Float) {
    A4("A4", 210f, 297f),
    A5("A5", 148f, 210f),
    A6("A6", 105f, 148f),
    B5("B5", 176f, 250f),
    LETTER("Letter", 216f, 279f),
    SCREEN("По экрану", 200f, 300f),
    POSTCARD("Открытка", 100f, 150f),
    WIDE("Широкий", 280f, 200f),
    CUSTOM("Свой размер", 210f, 297f),
}

enum class Orientation(val title: String) { PORTRAIT("Портрет"), LANDSCAPE("Ландшафт") }

enum class CoverMaterial(val title: String) {
    LEATHER("Кожа"), FABRIC("Ткань"), KRAFT("Крафт"), PLAIN("Гладкая")
}

enum class NotebookTemplate(val title: String) { NOTEBOOK("Тетрадь"), DIARY("Дневник") }

const val MM_TO_PT = 72f / 25.4f

data class Notebook(
    val id: String,
    val title: String,
    val template: NotebookTemplate = NotebookTemplate.DIARY,
    val coverColor: Int,
    val coverMaterial: CoverMaterial = CoverMaterial.LEATHER,
    val elastic: Boolean = true,
    val ribbon: Boolean = true,
    val paperType: PaperType = PaperType.LINED,
    val paperColor: Int,
    val format: PageFormat = PageFormat.A5,
    val orientation: Orientation = Orientation.PORTRAIT,
    val customWidthMm: Float? = null,
    val customHeightMm: Float? = null,
    val groupName: String? = null,
    /** Идентификаторы страниц по порядку. */
    val pages: List<String> = emptyList(),
    /** Дата каждой страницы (для дневника), по id. */
    val pageDates: Map<String, Long> = emptyMap(),
    val lastPage: Int = 0,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    val pageWidth: Float
        get() = (if (orientation == Orientation.PORTRAIT) (customWidthMm ?: format.widthMm) else (customHeightMm ?: format.heightMm)) * MM_TO_PT
    val pageHeight: Float
        get() = (if (orientation == Orientation.PORTRAIT) (customHeightMm ?: format.heightMm) else (customWidthMm ?: format.widthMm)) * MM_TO_PT
    val aspect: Float get() = pageWidth / pageHeight
}

data class NotebookSpec(
    val title: String,
    val template: NotebookTemplate,
    val coverColor: Int,
    val coverMaterial: CoverMaterial,
    val elastic: Boolean,
    val ribbon: Boolean,
    val paperType: PaperType,
    val paperColor: Int,
    val format: PageFormat,
    val orientation: Orientation,
    val customWidthMm: Float? = null,
    val customHeightMm: Float? = null,
    val groupName: String? = null,
)

object Palette {
    val coverColors = listOf(
        0xFF1E2A44.toInt(), 0xFF2F4858.toInt(), 0xFF33658A.toInt(), 0xFF3E6259.toInt(),
        0xFF55752F.toInt(), 0xFF8C6A3F.toInt(), 0xFFB5651D.toInt(), 0xFFE8734A.toInt(),
        0xFFA23B4C.toInt(), 0xFF6D3B6F.toInt(), 0xFF2B2B2E.toInt(), 0xFFD9CBB0.toInt(),
    )
    val paperColors = listOf(
        0xFFFFFFFF.toInt(), 0xFFFBF6EA.toInt(), 0xFFEDEDED.toInt(), 0xFF26282E.toInt(),
    )
    val defaultFavorites = listOf(
        0xFF1A1A1A.toInt(), 0xFF1F4FB5.toInt(), 0xFFD0312D.toInt(), 0xFF1E8C4E.toInt(),
        0xFFE8734A.toInt(), 0xFF7B3FB0.toInt(), 0xFFFFD43B.toInt(), 0xFF74C0FC.toInt(),
    )
    val presets: List<Pair<String, List<Int>>> = listOf(
        "Ручки" to listOf(0xFF1A1A1A, 0xFF1F4FB5, 0xFFD0312D, 0xFF1E8C4E, 0xFF5C3A21, 0xFF7B3FB0),
        "Пастель" to listOf(0xFFFFADAD, 0xFFFFD6A5, 0xFFFDFFB6, 0xFFCAFFBF, 0xFF9BF6FF, 0xFFBDB2FF),
        "Маркеры" to listOf(0xFFFFE066, 0xFFFF8FAB, 0xFF8CE99A, 0xFF74C0FC, 0xFFFFA94D, 0xFFD0BFFF),
        "Винтаж" to listOf(0xFF3D405B, 0xFFE07A5F, 0xFF81B29A, 0xFFF2CC8F, 0xFF8D5B4C, 0xFF5F0F40),
    ).map { (n, c) -> n to c.map { it.toInt() } }
}
