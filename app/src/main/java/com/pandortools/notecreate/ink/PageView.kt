package com.pandortools.notecreate.ink

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.pandortools.notecreate.data.ImageItem
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.data.PageData
import com.pandortools.notecreate.data.PageTurnStyle
import com.pandortools.notecreate.data.PenType
import com.pandortools.notecreate.data.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class EditorTool { PEN, ERASER, LASSO }
enum class EraserMode { STROKE, AREA }

data class SelectionInfo(
    val strokes: Int,
    val images: Int,
    val singleImageLocked: Boolean?,
)

/** Страница, на которую листаем. pageId == null — новая страница, которую создаст хозяин. */
data class TurnTarget(val pageId: String?, val page: PageData, val date: Long?)

/**
 * Холст одной страницы: письмо стилусом, ластик, лассо, картинки, масштаб и перелистывание.
 * Данные страницы неизменяемы; каждая правка — новый [PageData] и запись в историю отмены.
 */
@SuppressLint("ViewConstructor")
class PageView(context: Context) : View(context) {

    // ---------- Настройки, которые задаёт экран ----------
    var notebook: Notebook? = null
        set(value) {
            if (value == field) return
            val sizeChanged = field?.pageWidth != value?.pageWidth || field?.pageHeight != value?.pageHeight
            field = value
            if (sizeChanged) resetZoom() else invalidateCaches()
        }
    var tool = EditorTool.PEN
        set(value) { if (value == field) return; field = value; if (value != EditorTool.LASSO && selection == null) lassoPts.clear(); invalidate() }
    var pen = PenType.BALLPOINT
    var color = Color.BLACK
    var penWidth = 1.6f
    var eraserMode = EraserMode.STROKE
    var eraserSize = 8f
    var eraseOnlyHighlighter = false
    var fingerDraw = false
    var smoothing = 0.35f
    var turnStyle = PageTurnStyle.CURL
    var deskColor = Color.rgb(233, 228, 218)
        set(value) { field = value; invalidate() }

    var imageLoader: (ImageItem) -> Bitmap? = { null }

    // ---------- Обратные вызовы ----------
    var onPageEdited: ((PageData) -> Unit)? = null
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onSelectionChanged: ((SelectionInfo?) -> Unit)? = null
    /** Долгое нажатие пальцем: координаты на экране и на странице, id картинки под пальцем. */
    var onLongPress: ((vx: Float, vy: Float, px: Float, py: Float, imageId: String?) -> Unit)? = null
    var turnProvider: ((dir: Int) -> TurnTarget?)? = null
    var onTurned: ((dir: Int, target: TurnTarget) -> Unit)? = null
    var onDropImage: ((DragEvent, px: Float, py: Float) -> Boolean)? = null

    // ---------- Состояние страницы ----------
    var page: PageData = PageData()
        private set
    private var pageKey: String? = null
    private var lastExternal: PageData? = null
    private var pageDate: Long? = null
    private val undoStack = ArrayList<PageData>()
    private val redoStack = ArrayList<PageData>()

    private val renderer = PageRenderer.ui
    private val curl = PageCurl()
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // Матрица страница → экран
    private val baseMatrix = Matrix()
    private val userMatrix = Matrix()
    private val viewMatrix = Matrix()
    private val inverse = Matrix()
    private val pageRectView = RectF()

    // Кэш: фон (бумага + картинки) и чернила, отрисованные с cacheMatrix
    private var bgCache: Bitmap? = null
    private var inkCache: Bitmap? = null
    private val cacheMatrix = Matrix()
    private var cacheValid = false
    private var excludeStrokes: Set<Int> = emptySet()
    private var excludeImages: Set<String> = emptySet()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val uiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val dash = DashPathEffect(floatArrayOf(10f, 8f), 0f)

    init {
        isFocusable = true
        setOnDragListener { _, e -> handleDrag(e) }
    }

    // =====================================================================
    // Публичное API
    // =====================================================================

    /** Показать страницу. Если key совпадает с текущим — ничего не делаем. */
    fun setPage(key: String, data: PageData, date: Long?) {
        // Повторная доставка того же состояния от экрана — игнорируем, чтобы не сбить текущий жест.
        if (key == pageKey && (data === page || data === lastExternal)) return
        lastExternal = data
        if (key != pageKey) {
            undoStack.clear(); redoStack.clear()
            clearSelection()
        }
        pageKey = key
        page = data
        pageDate = date
        invalidateCaches()
        notifyHistory()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        clearSelection()
        redoStack += page
        page = undoStack.removeAt(undoStack.size - 1)
        afterEdit(recordHistory = false)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        clearSelection()
        undoStack += page
        page = redoStack.removeAt(redoStack.size - 1)
        afterEdit(recordHistory = false)
    }

    /** Внешняя правка страницы (вставка картинки и т. п.). */
    fun edit(transform: (PageData) -> PageData) {
        commit(transform(page))
    }

    fun insertImage(item: ImageItem) {
        commit(page.copy(images = page.images + item))
        selection = Selection(emptySet(), setOf(item.id))
        updateSelectionBounds()
        notifySelection()
    }

    /** Центр видимой части страницы (куда вставлять картинку). */
    fun visibleCenter(): PointF {
        val p = floatArrayOf(width / 2f, height / 2f)
        inverse.mapPoints(p)
        return PointF(p[0], p[1])
    }

    fun deleteSelection() {
        val sel = selection ?: return
        commit(page.copy(
            strokes = page.strokes.filterIndexed { i, _ -> i !in sel.strokes },
            images = page.images.filter { it.id !in sel.images },
        ))
        clearSelection()
    }

    fun recolorSelection(newColor: Int) {
        val sel = selection ?: return
        commit(page.copy(strokes = page.strokes.mapIndexed { i, s -> if (i in sel.strokes) s.recolored(newColor) else s }))
    }

    fun duplicateSelection() {
        val sel = selection ?: return
        val off = 12f
        val m = Matrix().apply { setTranslate(off, off) }
        val newStrokes = sel.strokes.sorted().map { page.strokes[it].transformed(m) }
        val newImages = page.images.filter { it.id in sel.images }.map {
            it.copy(id = java.util.UUID.randomUUID().toString(), x = it.x + off, y = it.y + off, locked = false)
        }
        val base = page.strokes.size
        commit(page.copy(strokes = page.strokes + newStrokes, images = page.images + newImages))
        selection = Selection(newStrokes.indices.map { base + it }.toSet(), newImages.map { it.id }.toSet())
        updateSelectionBounds()
        notifySelection()
    }

    fun toggleLockSelection() {
        val sel = selection ?: return
        commit(page.copy(images = page.images.map { if (it.id in sel.images) it.copy(locked = !it.locked) else it }))
        clearSelection()
    }

    fun setImageLocked(imageId: String, locked: Boolean) {
        commit(page.copy(images = page.images.map { if (it.id == imageId) it.copy(locked = locked) else it }))
    }

    fun deleteImage(imageId: String) {
        commit(page.copy(images = page.images.filter { it.id != imageId }))
    }

    fun bringSelectionToFront() {
        val sel = selection ?: return
        val (sel1, rest) = page.images.partition { it.id in sel.images }
        commit(page.copy(images = rest + sel1))
    }

    fun sendSelectionToBack() {
        val sel = selection ?: return
        val (sel1, rest) = page.images.partition { it.id in sel.images }
        commit(page.copy(images = sel1 + rest))
    }

    fun selectImage(imageId: String) {
        selection = Selection(emptySet(), setOf(imageId))
        updateSelectionBounds()
        notifySelection()
        invalidate()
    }

    fun clearSelection() {
        if (selection == null) return
        selection = null
        selTransforming = false
        excludeStrokes = emptySet(); excludeImages = emptySet()
        invalidateCaches()
        notifySelection()
    }

    fun resetZoom() {
        userMatrix.reset()
        updateMatrices()
        invalidateCaches()
    }

    // =====================================================================
    // Матрицы и кэш
    // =====================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgCache?.recycle(); inkCache?.recycle()
        bgCache = null; inkCache = null
        updateMatrices()
        invalidateCaches()
    }

    private fun updateMatrices() {
        val nb = notebook ?: return
        if (width == 0 || height == 0) return
        val margin = 16f * density
        val s = min((width - 2 * margin) / nb.pageWidth, (height - 2 * margin) / nb.pageHeight)
        baseMatrix.setScale(s, s)
        baseMatrix.postTranslate((width - nb.pageWidth * s) / 2f, (height - nb.pageHeight * s) / 2f)
        viewMatrix.set(baseMatrix)
        viewMatrix.postConcat(userMatrix)
        viewMatrix.invert(inverse)
        pageRectView.set(0f, 0f, nb.pageWidth, nb.pageHeight)
        viewMatrix.mapRect(pageRectView)
    }

    private fun currentScale(): Float {
        val v = FloatArray(9); viewMatrix.getValues(v); return v[Matrix.MSCALE_X]
    }

    private fun userScale(): Float {
        val v = FloatArray(9); userMatrix.getValues(v); return v[Matrix.MSCALE_X]
    }

    private fun invalidateCaches() {
        cacheValid = false
        invalidate()
    }

    private fun ensureCaches() {
        if (cacheValid || width == 0 || height == 0) return
        val nb = notebook ?: return
        updateMatrices()
        val bg = bgCache ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bgCache = it }
        val ink = inkCache ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { inkCache = it }
        bg.eraseColor(Color.TRANSPARENT)
        ink.eraseColor(Color.TRANSPARENT)
        val c1 = Canvas(bg)
        c1.concat(viewMatrix)
        renderer.drawPaper(c1, nb, pageDate)
        for (im in page.images) if (im.id !in excludeImages) renderer.drawImage(c1, im, imageLoader(im))
        renderInk(ink)
        cacheMatrix.set(viewMatrix)
        cacheValid = true
    }

    private fun renderInk(ink: Bitmap) {
        ink.eraseColor(Color.TRANSPARENT)
        val c = Canvas(ink)
        c.concat(viewMatrix)
        page.strokes.forEachIndexed { i, s -> if (s.pen == PenType.HIGHLIGHTER && i !in excludeStrokes) renderer.drawStroke(c, s) }
        page.strokes.forEachIndexed { i, s -> if (s.pen != PenType.HIGHLIGHTER && i !in excludeStrokes) renderer.drawStroke(c, s) }
    }

    // =====================================================================
    // Отрисовка
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(deskColor)
        val nb = notebook ?: return
        val turn = turnState
        if (turn != null) { drawTurn(canvas, turn, nb); return }

        ensureCaches()
        drawPageShadow(canvas)
        val delta = Matrix()
        val inv = Matrix()
        cacheMatrix.invert(inv)
        delta.set(inv)
        delta.postConcat(viewMatrix)
        canvas.save()
        canvas.concat(delta)
        bgCache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        inkCache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.restore()

        canvas.save()
        canvas.concat(viewMatrix)
        // Текущий штрих
        if (curCount > 0) renderer.drawStroke(canvas, Stroke(curPen, curColor, curWidth, curPts.copyOf(curCount * Stroke.STRIDE)))
        // Выделение
        selection?.let { drawSelection(canvas, it) }
        // Лассо
        if (lassoPts.size >= 4) {
            val p = Path()
            p.moveTo(lassoPts[0], lassoPts[1])
            var i = 2
            while (i < lassoPts.size) { p.lineTo(lassoPts[i], lassoPts[i + 1]); i += 2 }
            uiPaint.pathEffect = dash
            uiPaint.strokeWidth = 1.5f / currentScale()
            uiPaint.color = Color.rgb(232, 115, 74)
            canvas.drawPath(p, uiPaint)
            uiPaint.pathEffect = null
        }
        canvas.restore()

        // Курсор: кружок ластика или превью кисти при наведении
        val cx = cursorX; val cy = cursorY
        if (cx != null && cy != null) {
            val r = if (cursorEraser) eraserSize * currentScale() else max(2f, penWidth * currentScale() / 2f)
            uiPaint.strokeWidth = 1.5f
            uiPaint.color = Color.argb(160, 30, 42, 68)
            canvas.drawCircle(cx, cy, r, uiPaint)
            if (!cursorEraser) {
                uiPaint.color = color
                canvas.drawCircle(cx, cy, max(1f, r - 2f), uiPaint)
            }
        }
    }

    private fun drawPageShadow(canvas: Canvas) {
        shadowPaint.color = Color.WHITE
        shadowPaint.setShadowLayer(14f * density, 0f, 4f * density, Color.argb(60, 0, 0, 0))
        canvas.drawRect(pageRectView, shadowPaint)
        shadowPaint.clearShadowLayer()
    }

    private fun drawSelection(canvas: Canvas, sel: Selection) {
        val scale = currentScale()
        if (selTransforming) {
            canvas.save()
            canvas.concat(selMatrix)
            page.images.forEach { if (it.id in sel.images) renderer.drawImage(canvas, it, imageLoader(it)) }
            sel.strokes.forEach { i -> page.strokes.getOrNull(i)?.let { renderer.drawStroke(canvas, it) } }
            canvas.restore()
        }
        val b = RectF(sel.bounds)
        if (selTransforming) selMatrix.mapRect(b)
        uiPaint.pathEffect = dash
        uiPaint.strokeWidth = 1.5f / scale
        uiPaint.color = Color.rgb(51, 101, 138)
        canvas.drawRect(b, uiPaint)
        uiPaint.pathEffect = null
        val hr = HANDLE_DP * density / scale / 2f
        shadowPaint.color = Color.rgb(51, 101, 138)
        canvas.drawCircle(b.right, b.bottom, hr, shadowPaint)
        shadowPaint.color = Color.WHITE
        canvas.drawCircle(b.right, b.bottom, hr * 0.45f, shadowPaint)
    }

    // =====================================================================
    // Ввод
    // =====================================================================

    private enum class Mode { NONE, DRAW, ERASE, LASSO, SEL_MOVE, SEL_SCALE, PINCH, PAN, TURN_PENDING, FINGER_IDLE }

    private var mode = Mode.NONE
    private var activePointerId = -1
    private var lastStylusTime = 0L

    // текущий штрих
    private var curPts = FloatArray(4096)
    private var curCount = 0
    private var curPen = PenType.BALLPOINT
    private var curColor = Color.BLACK
    private var curWidth = 1.6f
    private var lastX = 0f
    private var lastY = 0f

    // ластик
    private var erasingTemp = false
    private var eraseWorking: MutableList<Stroke>? = null
    private var eraseChanged = false

    // лассо и выделение
    private val lassoPts = ArrayList<Float>()

    private class Selection(val strokes: Set<Int>, val images: Set<String>) {
        val bounds = RectF()
    }

    private var selection: Selection? = null
    private var selTransforming = false
    private val selMatrix = Matrix()
    private var selStartX = 0f
    private var selStartY = 0f

    // жесты пальцами
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var maxPointers = 0
    private var gestureMoved = false
    private var pinchStartSpan = 0f
    private var pinchStartFx = 0f
    private var pinchStartFy = 0f
    private val pinchStartMatrix = Matrix()
    private var panLastX = 0f
    private var panLastY = 0f
    private val longPressRunnable = Runnable { fireLongPress() }

    // курсор (наведение стилуса / ластик)
    private var cursorX: Float? = null
    private var cursorY: Float? = null
    private var cursorEraser = false

    private fun isStylus(e: MotionEvent, idx: Int): Boolean {
        val t = e.getToolType(idx)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    /** Palm contacts are substantially wider than finger contacts on digitizers. */
    private fun isPalmTouch(e: MotionEvent, idx: Int): Boolean {
        if (isStylus(e, idx)) return false
        val major = e.getToolMajor(idx)
        val minor = e.getToolMinor(idx)
        val threshold = 72f * density
        return (major >= threshold && minor >= threshold * 0.62f) || e.getSize(idx) >= 0.82f
    }

    private fun toPage(x: Float, y: Float): PointF {
        val p = floatArrayOf(x, y); inverse.mapPoints(p); return PointF(p[0], p[1])
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (notebook == null) return false
        if (turnAnimating) return true
        if (turnState != null) return handleTurn(e)

        val action = e.actionMasked
        val palmCanceled = action == MotionEvent.ACTION_POINTER_UP && (e.flags and MotionEvent.FLAG_CANCELED) != 0 &&
            !isStylus(e, e.actionIndex)
        val canceled = action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_POINTER_UP && (e.flags and MotionEvent.FLAG_CANCELED) != 0 &&
                e.getPointerId(e.actionIndex) == activePointerId)

        if (palmCanceled) {
            if (mode == Mode.FINGER_IDLE || mode == Mode.TURN_PENDING || mode == Mode.PAN || mode == Mode.PINCH) {
                mode = Mode.NONE
                removeCallbacks(longPressRunnable)
            }
            return true
        }
        if (canceled) {
            cancelGesture()
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                if (isStylus(e, idx)) {
                    // Стилус всегда главнее пальцев (отклонение ладони).
                    if (mode != Mode.NONE && mode != Mode.FINGER_IDLE && mode != Mode.PAN && mode != Mode.PINCH &&
                        mode != Mode.TURN_PENDING) return true
                    removeCallbacks(longPressRunnable)
                    lastStylusTime = e.eventTime
                    activePointerId = e.getPointerId(idx)
                    requestUnbufferedDispatch(e)
                    startPrimary(e, idx, stylus = true)
                } else {
                    // Reject broad palm contacts before they can start drawing or a gesture.
                    if (isPalmTouch(e, idx)) {
                        if (mode == Mode.FINGER_IDLE || mode == Mode.TURN_PENDING || mode == Mode.PAN || mode == Mode.PINCH) {
                            mode = Mode.NONE
                            removeCallbacks(longPressRunnable)
                        }
                        return true
                    }
                    val stylusRecently = e.eventTime - lastStylusTime < 600
                    if (mode == Mode.DRAW || mode == Mode.ERASE || mode == Mode.LASSO ||
                        mode == Mode.SEL_MOVE || mode == Mode.SEL_SCALE) {
                        if (activePointerIsStylus || stylusRecently) return true
                        // Второй палец во время рисования пальцем — отменяем штрих и масштабируем.
                        cancelGesture()
                    }
                    if (stylusRecently && e.pointerCount == 1) return true
                    onFingerDown(e)
                }
            }
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activePointerId &&
                    (mode == Mode.DRAW || mode == Mode.ERASE || mode == Mode.LASSO || mode == Mode.SEL_MOVE || mode == Mode.SEL_SCALE)) {
                    finishPrimary(e)
                } else if (mode == Mode.PINCH && e.pointerCount - 1 < 2) {
                    finishPinch(e)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                when (mode) {
                    Mode.DRAW, Mode.ERASE, Mode.LASSO, Mode.SEL_MOVE, Mode.SEL_SCALE -> finishPrimary(e)
                    Mode.PINCH -> finishPinch(e)
                    Mode.FINGER_IDLE, Mode.TURN_PENDING -> onFingerTap(e)
                    else -> Unit
                }
                if (mode == Mode.PAN) invalidateCaches()
                mode = Mode.NONE
                activePointerId = -1
            }
        }
        return true
    }

    private var activePointerIsStylus = false

    private fun startPrimary(e: MotionEvent, idx: Int, stylus: Boolean) {
        activePointerIsStylus = stylus
        activePointerId = e.getPointerId(idx)
        val x = e.getX(idx)
        val y = e.getY(idx)
        val p = toPage(x, y)

        // Сначала — взаимодействие с выделением
        val sel = selection
        if (sel != null) {
            val hr = HANDLE_DP * density / currentScale()
            if (hypot(p.x - sel.bounds.right, p.y - sel.bounds.bottom) <= hr) {
                beginSelTransform(Mode.SEL_SCALE, p); return
            }
            val grow = 10f * density / currentScale()
            val r = RectF(sel.bounds); r.inset(-grow, -grow)
            if (r.contains(p.x, p.y)) { beginSelTransform(Mode.SEL_MOVE, p); return }
            clearSelection()
        }

        val buttonEraser = e.getToolType(idx) == MotionEvent.TOOL_TYPE_ERASER ||
            (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
            (e.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        erasingTemp = buttonEraser

        when {
            buttonEraser || tool == EditorTool.ERASER -> {
                mode = Mode.ERASE
                undoBase = page
                eraseWorking = page.strokes.toMutableList()
                eraseChanged = false
                cursorEraser = true
                eraseAt(p.x, p.y)
                cursorX = x; cursorY = y
            }
            tool == EditorTool.LASSO -> {
                mode = Mode.LASSO
                lassoPts.clear()
                lassoPts += p.x; lassoPts += p.y
            }
            else -> {
                mode = Mode.DRAW
                curPen = pen; curColor = color; curWidth = penWidth
                curCount = 0
                lastX = p.x; lastY = p.y
                addPoint(p.x, p.y, pressureOf(e, idx, -1), tiltOf(e, idx, -1), force = true)
                cursorX = null
            }
        }
        invalidate()
    }

    private fun pressureOf(e: MotionEvent, idx: Int, h: Int): Float {
        if (!isStylus(e, idx)) return 0.5f
        val p = if (h < 0) e.getPressure(idx) else e.getHistoricalPressure(idx, h)
        return p.coerceIn(0f, 1f)
    }

    private fun tiltOf(e: MotionEvent, idx: Int, h: Int): Float {
        if (!isStylus(e, idx)) return 0f
        val t = if (h < 0) e.getAxisValue(MotionEvent.AXIS_TILT, idx)
        else e.getHistoricalAxisValue(MotionEvent.AXIS_TILT, idx, h)
        return (t / (PI.toFloat() / 2f)).coerceIn(0f, 1f)
    }

    private fun addPoint(x: Float, y: Float, pressure: Float, tilt: Float, force: Boolean = false) {
        // Стабилизатор: экспоненциальное сглаживание
        val k = 1f - smoothing.coerceIn(0f, 0.95f) * 0.85f
        val sx = if (curCount == 0) x else lastX + (x - lastX) * k
        val sy = if (curCount == 0) y else lastY + (y - lastY) * k
        if (!force && curCount > 0 && hypot(sx - lastX, sy - lastY) < 0.12f) return
        if ((curCount + 1) * Stroke.STRIDE > curPts.size) curPts = curPts.copyOf(curPts.size * 2)
        val o = curCount * Stroke.STRIDE
        curPts[o] = sx; curPts[o + 1] = sy; curPts[o + 2] = pressure; curPts[o + 3] = tilt
        curCount++
        lastX = sx; lastY = sy
    }

    private fun onMove(e: MotionEvent) {
        when (mode) {
            Mode.DRAW, Mode.ERASE, Mode.LASSO, Mode.SEL_MOVE, Mode.SEL_SCALE -> {
                val idx = e.findPointerIndex(activePointerId)
                if (idx < 0) return
                for (h in 0 until e.historySize) primaryMove(e, idx, e.getHistoricalX(idx, h), e.getHistoricalY(idx, h), h)
                primaryMove(e, idx, e.getX(idx), e.getY(idx), -1)
                invalidate()
            }
            Mode.PINCH -> onPinchMove(e)
            Mode.PAN -> {
                val dx = e.x - panLastX
                val dy = e.y - panLastY
                panLastX = e.x; panLastY = e.y
                userMatrix.postTranslate(dx, dy)
                clampUser()
                updateMatrices()
                invalidate()
            }
            Mode.FINGER_IDLE, Mode.TURN_PENDING -> {
                val dx = e.x - downX
                val dy = e.y - downY
                if (hypot(dx, dy) > touchSlop) {
                    removeCallbacks(longPressRunnable)
                    gestureMoved = true
                    if (mode == Mode.TURN_PENDING && abs(dx) > abs(dy)) {
                        val dir = if (downX > width / 2f) 1 else -1
                        if ((dir == 1 && dx < 0) || (dir == -1 && dx > 0)) { startTurn(dir, e); return }
                    }
                    if (userScale() > 1.01f) {
                        mode = Mode.PAN; panLastX = e.x; panLastY = e.y
                    } else if (fingerDraw && e.pointerCount == 1) {
                        // рисование пальцем
                        val fake = MotionEvent.obtain(e)
                        fake.setLocation(downX, downY)
                        startPrimary(fake, 0, stylus = false)
                        fake.recycle()
                    } else {
                        mode = Mode.NONE
                    }
                }
            }
            else -> Unit
        }
    }

    private fun primaryMove(e: MotionEvent, idx: Int, x: Float, y: Float, h: Int) {
        val p = toPage(x, y)
        when (mode) {
            Mode.DRAW -> addPoint(p.x, p.y, pressureOf(e, idx, h), tiltOf(e, idx, h))
            Mode.ERASE -> { eraseAt(p.x, p.y); cursorX = x; cursorY = y }
            Mode.LASSO -> {
                val n = lassoPts.size
                if (hypot(p.x - lassoPts[n - 2], p.y - lassoPts[n - 1]) > 2f) { lassoPts += p.x; lassoPts += p.y }
            }
            Mode.SEL_MOVE -> {
                selMatrix.setTranslate(p.x - selStartX, p.y - selStartY)
            }
            Mode.SEL_SCALE -> {
                val sel = selection ?: return
                val b = sel.bounds
                val w0 = max(1f, selStartX - b.left)
                val h0 = max(1f, selStartY - b.top)
                val s = max((p.x - b.left) / w0, (p.y - b.top) / h0).coerceIn(0.1f, 10f)
                selMatrix.setScale(s, s, b.left, b.top)
            }
            else -> Unit
        }
    }

    private fun finishPrimary(e: MotionEvent) {
        when (mode) {
            Mode.DRAW -> commitStroke()
            Mode.ERASE -> finishErase()
            Mode.LASSO -> finishLasso()
            Mode.SEL_MOVE, Mode.SEL_SCALE -> finishSelTransform()
            else -> Unit
        }
        mode = Mode.NONE
        activePointerId = -1
        cursorEraser = false
        if (!activePointerIsStylus) { cursorX = null; cursorY = null }
        invalidate()
    }

    private fun cancelGesture() {
        removeCallbacks(longPressRunnable)
        when (mode) {
            Mode.DRAW -> curCount = 0
            Mode.ERASE -> { eraseWorking = null; undoBase?.let { page = it }; undoBase = null; invalidateCaches() }
            Mode.LASSO -> lassoPts.clear()
            Mode.SEL_MOVE, Mode.SEL_SCALE -> { selMatrix.reset(); selTransforming = false; excludeStrokes = emptySet(); excludeImages = emptySet(); invalidateCaches() }
            Mode.PAN, Mode.PINCH -> invalidateCaches()
            else -> Unit
        }
        mode = Mode.NONE
        activePointerId = -1
        cursorX = null; cursorY = null
        invalidate()
    }

    // ---------- Штрих ----------

    private fun commitStroke() {
        if (curCount == 0) return
        val stroke = Stroke(curPen, curColor, curWidth, curPts.copyOf(curCount * Stroke.STRIDE))
        curCount = 0
        val newPage = page.copy(strokes = page.strokes + stroke)
        // Быстрый путь: дорисовываем штрих прямо в кэш
        val ink = inkCache
        if (cacheValid && ink != null && cacheMatrix == viewMatrix && stroke.pen != PenType.HIGHLIGHTER) {
            val c = Canvas(ink); c.concat(viewMatrix); renderer.drawStroke(c, stroke)
            pushHistory(); page = newPage; onPageEdited?.invoke(page); notifyHistory()
        } else {
            commit(newPage)
        }
    }

    // ---------- Ластик ----------

    private fun eraseAt(px: Float, py: Float) {
        val list = eraseWorking ?: return
        val r = eraserSize
        val onlyHl = eraseOnlyHighlighter && !erasingTemp
        if (eraserMode == EraserMode.STROKE) {
            val before = list.size
            list.removeAll { (!onlyHl || it.pen == PenType.HIGHLIGHTER) && Geometry.circleHitsStroke(it, px, py, r) }
            if (list.size != before) {
                eraseChanged = true
                page = page.copy(strokes = list.toList())
                inkCache?.let { renderInk(it); cacheMatrix.set(viewMatrix) }
            }
        } else {
            var changed = false
            val out = ArrayList<Stroke>(list.size)
            for (s in list) {
                if (onlyHl && s.pen != PenType.HIGHLIGHTER) { out += s; continue }
                val parts = Geometry.eraseCircle(s, px, py, r)
                if (parts == null) out += s else { out.addAll(parts); changed = true }
            }
            if (changed) {
                eraseChanged = true
                list.clear(); list.addAll(out)
                // мгновенно стираем в кэше, точный перерасчёт — в конце жеста
                val ink = inkCache
                if (ink != null && cacheValid) {
                    val c = Canvas(ink); c.concat(viewMatrix)
                    c.drawCircle(px, py, r, clearPaint)
                } else invalidateCaches()
            }
        }
    }

    private fun finishErase() {
        val list = eraseWorking
        eraseWorking = null
        cursorX = null; cursorY = null
        if (list == null || !eraseChanged) { page = undoAwarePageRestore(); return }
        val original = undoBase ?: page
        undoBase = null
        val result = page.copy(strokes = list.toList())
        page = original
        commit(result)
    }

    /** Страница до начала стирания штрихом (стираем «вживую», а в историю кладём исходник). */
    private var undoBase: PageData? = null
    private fun undoAwarePageRestore(): PageData { val b = undoBase ?: page; undoBase = null; return b }

    // ---------- Лассо и выделение ----------

    private fun finishLasso() {
        val region = Geometry.lassoRegion(lassoPts)
        lassoPts.clear()
        if (region == null) return
        val strokes = page.strokes.indices.filter { Geometry.strokeInRegion(page.strokes[it], region) }.toSet()
        val images = page.images.filter { !it.locked && Geometry.imageInRegion(it, region) }.map { it.id }.toSet()
        if (strokes.isEmpty() && images.isEmpty()) return
        selection = Selection(strokes, images)
        updateSelectionBounds()
        notifySelection()
    }

    private fun updateSelectionBounds() {
        val sel = selection ?: return
        val b = RectF()
        var first = true
        fun add(r: RectF) { if (first) { b.set(r); first = false } else b.union(r) }
        sel.strokes.forEach { page.strokes.getOrNull(it)?.let { s -> add(s.bounds) } }
        page.images.forEach { if (it.id in sel.images) add(it.rect) }
        sel.bounds.set(b)
    }

    private fun beginSelTransform(m: Mode, p: PointF) {
        val sel = selection ?: return
        mode = m
        selStartX = p.x; selStartY = p.y
        selMatrix.reset()
        selTransforming = true
        excludeStrokes = sel.strokes
        excludeImages = sel.images
        cacheValid = false
    }

    private fun finishSelTransform() {
        val sel = selection ?: return
        val m = Matrix(selMatrix)
        selTransforming = false
        excludeStrokes = emptySet(); excludeImages = emptySet()
        selMatrix.reset()
        if (m.isIdentity) { invalidateCaches(); return }
        val v = FloatArray(9); m.getValues(v)
        val sc = v[Matrix.MSCALE_X]
        val newStrokes = page.strokes.mapIndexed { i, s -> if (i in sel.strokes) s.transformed(m) else s }
        val newImages = page.images.map { im ->
            if (im.id !in sel.images) im else {
                val pt = floatArrayOf(im.x, im.y); m.mapPoints(pt)
                im.copy(x = pt[0], y = pt[1], w = im.w * sc, h = im.h * sc)
            }
        }
        commit(page.copy(strokes = newStrokes, images = newImages))
        updateSelectionBounds()
        notifySelection()
    }

    private fun notifySelection() {
        val sel = selection
        if (sel == null) { onSelectionChanged?.invoke(null); invalidate(); return }
        val single = if (sel.images.size == 1 && sel.strokes.isEmpty())
            page.images.firstOrNull { it.id in sel.images }?.locked else null
        onSelectionChanged?.invoke(SelectionInfo(sel.strokes.size, sel.images.size, single))
        invalidate()
    }

    // ---------- Пальцы: масштаб, сдвиг, отмена, листание ----------

    private fun onFingerDown(e: MotionEvent) {
        val count = e.pointerCount
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = e.x; downY = e.y; downTime = e.eventTime
            maxPointers = 1; gestureMoved = false
            val edge = width * 0.14f
            mode = if ((downX < edge || downX > width - edge) && userScale() <= 1.01f) Mode.TURN_PENDING else Mode.FINGER_IDLE
            postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            return
        }
        removeCallbacks(longPressRunnable)
        maxPointers = max(maxPointers, count)
        if (count >= 2) {
            mode = Mode.PINCH
            pinchStartSpan = span(e)
            pinchStartFx = focusX(e); pinchStartFy = focusY(e)
            pinchStartMatrix.set(userMatrix)
        }
    }

    private fun span(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 1f
        return hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
    }
    private fun focusX(e: MotionEvent) = if (e.pointerCount < 2) e.x else (e.getX(0) + e.getX(1)) / 2f
    private fun focusY(e: MotionEvent) = if (e.pointerCount < 2) e.y else (e.getY(0) + e.getY(1)) / 2f

    private fun onPinchMove(e: MotionEvent) {
        if (e.pointerCount < 2) return
        val s = span(e) / max(1f, pinchStartSpan)
        val fx = focusX(e); val fy = focusY(e)
        if (abs(s - 1f) > 0.04f || hypot(fx - pinchStartFx, fy - pinchStartFy) > touchSlop) gestureMoved = true
        userMatrix.set(pinchStartMatrix)
        userMatrix.postScale(s, s, pinchStartFx, pinchStartFy)
        userMatrix.postTranslate(fx - pinchStartFx, fy - pinchStartFy)
        clampUser()
        updateMatrices()
        invalidate()
    }

    private fun finishPinch(e: MotionEvent) {
        val quick = e.eventTime - downTime < 300
        if (!gestureMoved && quick) {
            if (maxPointers == 2) undo() else if (maxPointers >= 3) redo()
        }
        if (userScale() < 1.03f) userMatrix.reset()
        updateMatrices()
        invalidateCaches()
        mode = Mode.NONE
    }

    private fun clampUser() {
        val v = FloatArray(9); userMatrix.getValues(v)
        val s = v[Matrix.MSCALE_X]
        if (s < 1f) userMatrix.postScale(1f / s, 1f / s, width / 2f, height / 2f)
        if (s > 6f) userMatrix.postScale(6f / s, 6f / s, width / 2f, height / 2f)
    }

    private fun onFingerTap(e: MotionEvent) {
        if (gestureMoved || e.eventTime - downTime > 350) return
        val p = toPage(e.x, e.y)
        val hit = page.images.lastOrNull { !it.locked && Geometry.imageContains(it, p.x, p.y) }
        if (hit != null) selectImage(hit.id) else if (selection != null) clearSelection()
    }

    private fun fireLongPress() {
        if (mode != Mode.FINGER_IDLE && mode != Mode.TURN_PENDING) return
        if (gestureMoved) return
        val p = toPage(downX, downY)
        val hit = page.images.lastOrNull { Geometry.imageContains(it, p.x, p.y) }
        mode = Mode.NONE
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke(downX, downY, p.x, p.y, hit?.id)
    }

    // ---------- Наведение стилуса ----------

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isStylus(event, 0)) return super.onHoverEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                lastStylusTime = event.eventTime
                cursorX = event.x; cursorY = event.y
                cursorEraser = tool == EditorTool.ERASER ||
                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                    (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
            }
            MotionEvent.ACTION_HOVER_EXIT -> { cursorX = null; cursorY = null }
        }
        if (tool == EditorTool.LASSO && !cursorEraser) { cursorX = null; cursorY = null }
        invalidate()
        return true
    }

    // ---------- История ----------

    private fun pushHistory() {
        undoStack += page
        if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
        redoStack.clear()
    }

    private fun commit(newPage: PageData) {
        pushHistory()
        page = newPage
        afterEdit(recordHistory = true)
    }

    private fun afterEdit(recordHistory: Boolean) {
        if (selection != null) {
            // индексы штрихов могли измениться — проверяем, что выделение ещё валидно
            val sel = selection!!
            if (sel.strokes.any { it >= page.strokes.size } || sel.images.any { id -> page.images.none { it.id == id } }) {
                selection = null; onSelectionChanged?.invoke(null)
            } else updateSelectionBounds()
        }
        invalidateCaches()
        onPageEdited?.invoke(page)
        notifyHistory()
    }

    private fun notifyHistory() = onHistoryChanged?.invoke(undoStack.isNotEmpty(), redoStack.isNotEmpty())

    // =====================================================================
    // Перелистывание
    // =====================================================================

    private class TurnState(
        val dir: Int,
        val target: TurnTarget,
        val current: Bitmap,
        val other: Bitmap,
        val corner: PointF,
        val startX: Float,
        val startY: Float,
        val touch: PointF,
    )

    private var turnState: TurnState? = null
    private var turnAnimating = false

    private fun snapshotCurrent(): Bitmap {
        ensureCaches()
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        bgCache?.let { c.drawBitmap(it, 0f, 0f, null) }
        inkCache?.let { c.drawBitmap(it, 0f, 0f, null) }
        return b
    }

    private fun snapshotOf(t: TurnTarget, nb: Notebook): Bitmap {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.concat(viewMatrix)
        renderer.drawPage(c, nb, t.page, t.date, imageLoader)
        return b
    }

    private fun startTurn(dir: Int, e: MotionEvent) {
        val nb = notebook ?: return
        val target = turnProvider?.invoke(dir)
        if (target == null) { mode = Mode.NONE; return }
        clearSelection()
        userMatrix.reset(); updateMatrices(); cacheValid = false
        val cur = snapshotCurrent()
        val other = snapshotOf(target, nb)
        val corner = PointF(pageRectView.right, if (downY < pageRectView.centerY()) pageRectView.top else pageRectView.bottom)
        val w = pageRectView.width()
        val touch = if (dir == 1) PointF(corner.x, corner.y) else PointF(corner.x - 2 * w, corner.y)
        turnState = TurnState(dir, target, cur, other, corner, downX, downY, touch)
        mode = Mode.NONE
        handleTurn(e)
    }

    private fun handleTurn(e: MotionEvent): Boolean {
        val t = turnState ?: return true
        val w = pageRectView.width()
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                val dx = e.x - t.startX
                val dy = (e.y - t.startY) * 0.35f
                val baseX = if (t.dir == 1) t.corner.x else t.corner.x - 2 * w
                t.touch.set((baseX + dx * 2f).coerceIn(t.corner.x - 2 * w, t.corner.x), t.corner.y + dy)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val progress = (t.corner.x - t.touch.x) / (2 * w) // 0 — лист лежит, 1 — перевёрнут
                val complete = e.actionMasked == MotionEvent.ACTION_UP &&
                    if (t.dir == 1) progress > 0.25f else progress < 0.75f
                animateTurn(t, complete)
            }
        }
        return true
    }

    private fun animateTurn(t: TurnState, complete: Boolean) {
        val w = pageRectView.width()
        val endX = when {
            t.dir == 1 && complete -> t.corner.x - 2 * w
            t.dir == 1 -> t.corner.x
            complete -> t.corner.x
            else -> t.corner.x - 2 * w
        }
        val sx = t.touch.x; val sy = t.touch.y
        turnAnimating = true
        val duration = if (turnStyle == PageTurnStyle.NONE) 0L else 280L
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                t.touch.set(sx + (endX - sx) * f, sy + (t.corner.y - sy) * f)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    turnAnimating = false
                    turnState = null
                    t.current.recycle(); t.other.recycle()
                    if (complete) {
                        pageKey = t.target.pageId ?: "pending-${System.nanoTime()}"
                        page = t.target.page
                        pageDate = t.target.date
                        undoStack.clear(); redoStack.clear()
                        notifyHistory()
                        onTurned?.invoke(t.dir, t.target)
                    }
                    invalidateCaches()
                }
            })
            start()
        }
    }

    private fun drawTurn(canvas: Canvas, t: TurnState, nb: Notebook) {
        drawPageShadow(canvas)
        val w = pageRectView.width()
        when (turnStyle) {
            PageTurnStyle.CURL, PageTurnStyle.NONE -> {
                if (t.dir == 1) curl.draw(canvas, pageRectView, t.other, t.current, t.corner, t.touch, nb.paperColor)
                else curl.draw(canvas, pageRectView, t.current, t.other, t.corner, t.touch, nb.paperColor)
            }
            PageTurnStyle.SLIDE -> {
                val progress = (t.corner.x - t.touch.x) / (2 * w)
                canvas.save()
                canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
                if (t.dir == 1) {
                    canvas.drawBitmap(t.current, -progress * width, 0f, null)
                    canvas.drawBitmap(t.other, (1 - progress) * width, 0f, null)
                } else {
                    canvas.drawBitmap(t.other, -progress * width, 0f, null)
                    canvas.drawBitmap(t.current, (1 - progress) * width, 0f, null)
                }
                canvas.restore()
            }
        }
    }

    // ---------- Перетаскивание картинок из других приложений ----------

    private fun handleDrag(e: DragEvent): Boolean {
        return when (e.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                e.clipDescription?.let { d -> (0 until d.mimeTypeCount).any { d.getMimeType(it).startsWith("image/") } } == true
            DragEvent.ACTION_DROP -> {
                val p = toPage(e.x, e.y)
                onDropImage?.invoke(e, p.x, p.y) ?: false
            }
            else -> true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(longPressRunnable)
    }

    companion object {
        private const val MAX_HISTORY = 100
        private const val HANDLE_DP = 28f
    }
}
