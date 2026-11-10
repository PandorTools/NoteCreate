package com.pandortools.notecreate.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.ImageItem
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.data.NotebookRepository
import com.pandortools.notecreate.data.NotebookSpec
import com.pandortools.notecreate.data.NotebookTemplate
import com.pandortools.notecreate.data.PageData
import com.pandortools.notecreate.data.PageLayers
import com.pandortools.notecreate.ink.PageRenderer
import com.pandortools.notecreate.ink.TurnTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import kotlin.math.min

data class LoadedPage(val id: String, val data: PageData, val date: Long?)

class EditorViewModel(private val notebookId: String) : ViewModel() {

    private val repo: NotebookRepository = AppGraph.repository
    private val settings = AppGraph.settings

    private val _notebook = MutableStateFlow<Notebook?>(null)
    val notebook: StateFlow<Notebook?> = _notebook.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _page = MutableStateFlow<LoadedPage?>(null)
    val page: StateFlow<LoadedPage?> = _page.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Соседние страницы — для мгновенного перелистывания. */
    private val neighbors = HashMap<String, PageData>()
    /** Страницы, изменённые в этой сессии, — источник правды раньше диска. */
    private val edited = java.util.concurrent.ConcurrentHashMap<String, PageData>()
    /** Все изменения метаданных тетради идут строго по очереди. */
    private val metaMutex = Mutex()

    private suspend fun dataFor(nb: Notebook, id: String): PageData =
        edited[id] ?: neighbors[id] ?: repo.loadPage(nb, id)

    /** Изменить метаданные на основе самой свежей версии и сохранить. */
    private suspend fun updateNotebook(block: suspend (Notebook) -> Notebook): Notebook? =
        metaMutex.withLock {
            val cur = _notebook.value ?: return@withLock null
            val upd = block(cur)
            _notebook.value = upd
            repo.save(upd)
            upd
        }

    private var pendingSave: PageData? = null
    private var pendingPageId: String? = null
    private var saveJob: Job? = null

    private val imageCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtLeast(16 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        var nb = repo.get(notebookId) ?: run { repo.refresh(); repo.get(notebookId) } ?: return
        settings.update { it.copy(lastNotebookId = nb.id) }
        var start = nb.lastPage.coerceIn(0, nb.pages.size - 1)
        // Дневник: новый день — новая страница.
        if (nb.template == NotebookTemplate.DIARY && settings.value.newDayNewPage) {
            val lastId = nb.pages.last()
            val lastDate = nb.pageDates[lastId] ?: 0L
            if (!isToday(lastDate)) {
                val lastData = repo.loadPage(nb, lastId)
                if (lastData.isEmpty) {
                    nb = nb.copy(pageDates = nb.pageDates + (lastId to System.currentTimeMillis()))
                    repo.save(nb)
                } else {
                    nb = repo.insertPage(nb, nb.pages.size)
                }
                start = nb.pages.size - 1
            }
        }
        _notebook.value = nb
        goTo(start)
    }

    fun goTo(i: Int) {
        val nb = _notebook.value ?: return
        val target = i.coerceIn(0, nb.pages.size - 1)
        viewModelScope.launch {
            flush()
            val id = nb.pages[target]
            val data = dataFor(nb, id)
            _index.value = target
            _page.value = LoadedPage(id, data, nb.pageDates[id])
            rememberLastPage(target)
            preloadNeighbors()
        }
    }

    fun setPageDate(dateMillis: Long) {
        val current = _page.value ?: return
        flush()
        _page.value = current.copy(date = dateMillis)
        viewModelScope.launch {
            updateNotebook { it.copy(pageDates = it.pageDates + (current.id to dateMillis)) }
        }
    }

    fun toggleBookmark(pageIndex: Int = _index.value) {
        val nb = _notebook.value ?: return
        val id = nb.pages.getOrNull(pageIndex) ?: return
        saveScope.launch {
            updateNotebook { current ->
                val marks = current.bookmarks.toMutableSet()
                if (!marks.add(id)) marks.remove(id)
                current.copy(bookmarks = marks, updatedAt = System.currentTimeMillis())
            }
        }
    }

    fun importPdf(uri: Uri) = viewModelScope.launch {
        flush()
        val nb = _notebook.value ?: return@launch
        val imported = repo.importPdfPages(nb, uri)
        if (imported.isEmpty()) { _message.value = "Не удалось прочитать PDF"; return@launch }
        val first = _index.value + 1
        val ids = imported.indices.map { UUID.randomUUID().toString().substring(0, 13) }
        val dates = ids.associateWith { System.currentTimeMillis() }
        val updated = updateNotebook { current ->
            val pages = current.pages.toMutableList().apply { addAll(first.coerceIn(0, size), ids) }
            current.copy(pages = pages, pageDates = current.pageDates + dates, lastPage = first,
                updatedAt = System.currentTimeMillis())
        } ?: return@launch
        imported.forEachIndexed { i, image ->
            val data = PageData(images = listOf(ImageItem(
                id = UUID.randomUUID().toString(), fileName = image.fileName,
                x = 0f, y = 0f, w = updated.pageWidth, h = updated.pageHeight,
                locked = true, layerId = PageLayers.BACKGROUND,
            )))
            edited[ids[i]] = data
            repo.savePage(updated, ids[i], data)
        }
        _index.value = first
        _page.value = LoadedPage(ids.first(), edited.getValue(ids.first()), dates.getValue(ids.first()))
        _message.value = "Импортировано страниц PDF: ${imported.size}"
        preloadNeighbors()
    }

    private fun rememberLastPage(i: Int) {
        if (_notebook.value?.lastPage == i) return
        saveScope.launch { updateNotebook { it.copy(lastPage = i) } }
    }

    private fun preloadNeighbors() {
        val nb = _notebook.value ?: return
        val i = _index.value
        viewModelScope.launch {
            val keep = HashSet<String>()
            for (j in listOf(i - 1, i + 1)) {
                val id = nb.pages.getOrNull(j) ?: continue
                keep += id
                neighbors[id] = edited[id] ?: neighbors[id] ?: repo.loadPage(nb, id)
            }
            neighbors.keys.retainAll(keep)
        }
    }

    // ---------- Сохранение ----------

    fun onPageEdited(data: PageData) {
        val cur = _page.value ?: return
        _page.value = cur.copy(data = data)
        edited[cur.id] = data
        pendingSave = data
        pendingPageId = cur.id
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            flush()
        }
    }

    /** Немедленно записать несохранённое (вызывается при уходе со страницы и сворачивании). */
    fun flush() {
        val data = pendingSave ?: return
        val id = pendingPageId ?: return
        val nb = _notebook.value ?: return
        pendingSave = null
        pendingPageId = null
        saveScope.launch {
            repo.savePage(nb, id, data)
            updateNotebook { it.copy(updatedAt = System.currentTimeMillis()) }
        }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    // ---------- Перелистывание ----------

    fun turnTarget(dir: Int): TurnTarget? {
        val nb = _notebook.value ?: return null
        val j = _index.value + dir
        if (j < 0) return null
        if (j >= nb.pages.size) {
            // Листаем дальше последней страницы — будет новая чистая страница.
            return TurnTarget(null, PageData(), System.currentTimeMillis())
        }
        val id = nb.pages[j]
        val data = edited[id] ?: neighbors[id] ?: return null
        return TurnTarget(id, data, nb.pageDates[id])
    }

    fun onTurned(dir: Int, target: TurnTarget) {
        flush()
        val nb = _notebook.value ?: return
        if (target.pageId == null) {
            // id новой страницы известен сразу, чтобы правки не ушли на прошлую страницу
            val newId = UUID.randomUUID().toString().substring(0, 13)
            val date = target.date ?: System.currentTimeMillis()
            _index.value = nb.pages.size
            _page.value = LoadedPage(newId, target.page, date)
            viewModelScope.launch {
                updateNotebook { cur -> insertPageMeta(cur, cur.pages.size, date, newId) } ?: return@launch
                rememberLastPage(_index.value)
                preloadNeighbors()
            }
        } else {
            _index.value = _index.value + dir
            _page.value = LoadedPage(target.pageId, target.page, target.date)
            rememberLastPage(_index.value)
            preloadNeighbors()
        }
    }

    // ---------- Страницы ----------

    private fun insertPageMeta(
        nb: Notebook,
        index: Int,
        date: Long?,
        id: String = UUID.randomUUID().toString().substring(0, 13),
    ): Notebook {
        val pages = nb.pages.toMutableList().apply { add(index.coerceIn(0, size), id) }
        return nb.copy(
            pages = pages,
            pageDates = nb.pageDates + (id to (date ?: System.currentTimeMillis())),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun addPageAfterCurrent() = addPageAt(_index.value + 1)

    fun addPageAt(index: Int) {
        flush()
        viewModelScope.launch {
            updateNotebook { insertPageMeta(it, index, null) } ?: return@launch
            goTo(index)
        }
    }

    fun duplicatePage(index: Int) {
        flush()
        viewModelScope.launch {
            val nb0 = _notebook.value ?: return@launch
            val source = dataFor(nb0, nb0.pages.getOrNull(index) ?: return@launch)
            val updated = updateNotebook { insertPageMeta(it, index + 1, null) } ?: return@launch
            val newId = updated.pages[index + 1]
            edited[newId] = source
            repo.savePage(updated, newId, source)
            goTo(index + 1)
        }
    }

    fun deletePage(index: Int) {
        flush()
        viewModelScope.launch {
            val nb0 = _notebook.value ?: return@launch
            if (nb0.pages.size <= 1) {
                // единственную страницу не удаляем, а очищаем
                val id = nb0.pages[0]
                edited[id] = PageData()
                repo.savePage(nb0, id, PageData())
                _page.value = LoadedPage(id, PageData(), nb0.pageDates[id])
                return@launch
            }
            val removedId = nb0.pages.getOrNull(index) ?: return@launch
            val updated = updateNotebook { cur ->
                cur.copy(
                    pages = cur.pages.filter { it != removedId },
                    pageDates = cur.pageDates - removedId,
                    lastPage = cur.lastPage.coerceAtMost(cur.pages.size - 2),
                    updatedAt = System.currentTimeMillis(),
                )
            } ?: return@launch
            repo.deletePageFile(updated, removedId)
            edited.remove(removedId); neighbors.remove(removedId)
            _page.value = null
            goTo(min(index, updated.pages.size - 1))
        }
    }

    fun movePage(from: Int, to: Int) {
        val nb = _notebook.value ?: return
        if (to < 0 || to >= nb.pages.size) return
        flush()
        viewModelScope.launch {
            val updated = updateNotebook { cur ->
                val pages = cur.pages.toMutableList()
                val p = pages.removeAt(from)
                pages.add(to.coerceIn(0, pages.size), p)
                cur.copy(pages = pages, updatedAt = System.currentTimeMillis())
            } ?: return@launch
            goTo(updated.pages.indexOf(_page.value?.id).coerceAtLeast(0))
        }
    }

    fun applySpec(spec: NotebookSpec) {
        viewModelScope.launch {
            updateNotebook { cur ->
                cur.copy(
                    title = spec.title.ifBlank { cur.title }, template = spec.template,
                    coverColor = spec.coverColor, coverMaterial = spec.coverMaterial,
                    elastic = spec.elastic, ribbon = spec.ribbon, paperType = spec.paperType,
                    paperColor = spec.paperColor, format = spec.format, orientation = spec.orientation,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    // ---------- Картинки ----------

    fun loadImage(item: ImageItem): Bitmap? {
        val nb = _notebook.value ?: return null
        imageCache.get(item.fileName)?.let { return it }
        val bmp = repo.decodeImage(nb, item.fileName, 2048) ?: return null
        imageCache.put(item.fileName, bmp)
        return bmp
    }

    suspend fun importImage(uri: Uri): NotebookRepository.ImportedImage? {
        val nb = _notebook.value ?: return null
        return repo.importImage(nb, uri)
    }

    suspend fun importBitmap(bmp: Bitmap): NotebookRepository.ImportedImage? {
        val nb = _notebook.value ?: return null
        return repo.importBitmap(nb, bmp)
    }

    /** Картинка шириной до 60% страницы, по центру точки (cx, cy), не вылезает за лист. */
    fun placeImage(img: NotebookRepository.ImportedImage, cx: Float, cy: Float): ImageItem? {
        val nb = _notebook.value ?: return null
        val aspect = img.width.toFloat() / img.height.coerceAtLeast(1)
        var w = nb.pageWidth * 0.6f
        var h = w / aspect
        if (h > nb.pageHeight * 0.6f) { h = nb.pageHeight * 0.6f; w = h * aspect }
        val x = (cx - w / 2f).coerceIn(0f, (nb.pageWidth - w).coerceAtLeast(0f))
        val y = (cy - h / 2f).coerceIn(0f, (nb.pageHeight - h).coerceAtLeast(0f))
        return ImageItem(UUID.randomUUID().toString(), img.fileName, x, y, w, h)
    }

    // ---------- Миниатюры и экспорт ----------

    suspend fun thumbnail(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.Default) {
        val nb = _notebook.value ?: return@withContext null
        val id = nb.pages.getOrNull(index) ?: return@withContext null
        val data = edited[id] ?: if (_page.value?.id == id) _page.value!!.data else repo.loadPage(nb, id)
        val r = PageRenderer()
        r.renderBitmap(nb, data, nb.pageDates[id], widthPx) { im -> repo.decodeImage(nb, im.fileName, 512) }
    }

    fun exportPdf(context: Context, uri: Uri) {
        val nb = _notebook.value ?: return
        flush()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    delay(100)
                    val doc = PdfDocument()
                    val r = PageRenderer()
                    nb.pages.forEachIndexed { i, id ->
                        val data = edited[id] ?: repo.loadPage(nb, id)
                        val info = PdfDocument.PageInfo.Builder(nb.pageWidth.toInt(), nb.pageHeight.toInt(), i + 1).create()
                        val p = doc.startPage(info)
                        r.drawPage(p.canvas, nb, data, nb.pageDates[id]) { im -> repo.decodeImage(nb, im.fileName, 1600) }
                        doc.finishPage(p)
                    }
                    context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
                    doc.close()
                }.isSuccess
            }
            _message.value = if (ok) "PDF сохранён" else "Не удалось сохранить PDF"
        }
    }

    fun exportPng(context: Context, uri: Uri) {
        val nb = _notebook.value ?: return
        val cur = _page.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = PageRenderer().renderBitmap(nb, cur.data, cur.date, (nb.pageWidth * 3).toInt()) { im ->
                        repo.decodeImage(nb, im.fileName, 2560)
                    }
                    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                }.isSuccess
            }
            _message.value = if (ok) "Страница сохранена в PNG" else "Не удалось сохранить картинку"
        }
    }

    fun showMessage(text: String) { _message.value = text }
    fun consumeMessage() { _message.value = null }

    private fun isToday(time: Long): Boolean {
        if (time == 0L) return false
        val a = Calendar.getInstance().apply { timeInMillis = time }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        /** Сохранения переживают закрытие экрана. */
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
