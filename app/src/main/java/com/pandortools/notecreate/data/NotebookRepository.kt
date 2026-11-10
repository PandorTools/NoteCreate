package com.pandortools.notecreate.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Всё хранится локально в папке приложения:
 * notebooks/<id>/meta.json, notebooks/<id>/pages/<pageId>.ncp, notebooks/<id>/images/<file>.
 */
class NotebookRepository(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "notebooks").apply { mkdirs() }
    private val mutex = Mutex()

    private val _notebooks = MutableStateFlow<List<Notebook>>(emptyList())
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val list = root.listFiles()?.mapNotNull { dir ->
            runCatching { readMeta(File(dir, META)) }.getOrNull()
        }.orEmpty().filter { nb ->
            // Корзина хранит тетради 30 дней.
            val expired = nb.deletedAt != null && now - nb.deletedAt > TRASH_TTL
            if (expired) File(root, nb.id).deleteRecursively()
            !expired
        }
        _notebooks.value = list.sortedByDescending { it.updatedAt }
    }

    fun get(id: String): Notebook? = _notebooks.value.firstOrNull { it.id == id }

    suspend fun create(spec: NotebookSpec): Notebook = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val firstPage = newPageId()
        val nb = Notebook(
            id = id,
            title = spec.title.ifBlank { "Без названия" },
            template = spec.template,
            coverColor = spec.coverColor,
            coverMaterial = spec.coverMaterial,
            elastic = spec.elastic,
            ribbon = spec.ribbon,
            paperType = spec.paperType,
            paperColor = spec.paperColor,
            format = spec.format,
            orientation = spec.orientation,
            pages = listOf(firstPage),
            pageDates = mapOf(firstPage to System.currentTimeMillis()),
        )
        File(root, id).mkdirs()
        save(nb)
        nb
    }

    /** Сохраняет метаданные и обновляет список. */
    suspend fun save(nb: Notebook) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = File(root, nb.id).apply { mkdirs() }
            atomicWrite(File(dir, META)) { it.write(toJson(nb).toString().toByteArray()) }
        }
        _notebooks.value = (_notebooks.value.filter { it.id != nb.id } + nb).sortedByDescending { it.updatedAt }
    }

    suspend fun applySpec(nb: Notebook, spec: NotebookSpec): Notebook {
        val updated = nb.copy(
            title = spec.title.ifBlank { nb.title },
            template = spec.template,
            coverColor = spec.coverColor,
            coverMaterial = spec.coverMaterial,
            elastic = spec.elastic,
            ribbon = spec.ribbon,
            paperType = spec.paperType,
            paperColor = spec.paperColor,
            format = spec.format,
            orientation = spec.orientation,
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
        return updated
    }

    suspend fun moveToTrash(nb: Notebook) = save(nb.copy(deletedAt = System.currentTimeMillis()))
    suspend fun restore(nb: Notebook) = save(nb.copy(deletedAt = null))

    suspend fun deleteForever(nb: Notebook) = withContext(Dispatchers.IO) {
        File(root, nb.id).deleteRecursively()
        _notebooks.value = _notebooks.value.filter { it.id != nb.id }
    }

    // ---------- Страницы ----------

    suspend fun loadPage(nb: Notebook, pageId: String): PageData = withContext(Dispatchers.IO) {
        val f = pageFile(nb, pageId)
        if (!f.exists()) return@withContext PageData()
        mutex.withLock { runCatching { readPage(f) }.getOrElse { PageData() } }
    }

    suspend fun savePage(nb: Notebook, pageId: String, data: PageData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val f = pageFile(nb, pageId)
            f.parentFile?.mkdirs()
            atomicWrite(f) { out -> writePage(out, data) }
        }
    }

    suspend fun insertPage(nb: Notebook, index: Int, date: Long = System.currentTimeMillis()): Notebook {
        val id = newPageId()
        val pages = nb.pages.toMutableList().apply { add(index.coerceIn(0, size), id) }
        val updated = nb.copy(pages = pages, pageDates = nb.pageDates + (id to date), updatedAt = System.currentTimeMillis())
        save(updated)
        return updated
    }

    suspend fun duplicatePage(nb: Notebook, index: Int): Notebook {
        val source = loadPage(nb, nb.pages[index])
        val updated = insertPage(nb, index + 1)
        savePage(updated, updated.pages[index + 1], source)
        return updated
    }

    suspend fun deletePage(nb: Notebook, index: Int): Notebook {
        if (nb.pages.size <= 1) {
            // Последнюю страницу не удаляем, а очищаем.
            savePage(nb, nb.pages[0], PageData())
            return nb
        }
        val id = nb.pages[index]
        withContext(Dispatchers.IO) { pageFile(nb, id).delete() }
        val updated = nb.copy(
            pages = nb.pages.filterIndexed { i, _ -> i != index },
            pageDates = nb.pageDates - id,
            lastPage = nb.lastPage.coerceAtMost(nb.pages.size - 2),
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
        return updated
    }

    suspend fun deletePageFile(nb: Notebook, pageId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { pageFile(nb, pageId).delete() }
    }

    suspend fun movePage(nb: Notebook, from: Int, to: Int): Notebook {
        val pages = nb.pages.toMutableList()
        val p = pages.removeAt(from)
        pages.add(to.coerceIn(0, pages.size), p)
        val updated = nb.copy(pages = pages, updatedAt = System.currentTimeMillis())
        save(updated)
        return updated
    }

    // ---------- Картинки ----------

    fun imageFile(nb: Notebook, fileName: String) = File(File(File(root, nb.id), "images"), fileName)

    /** Копирует картинку внутрь тетради (сжимая до 2560 px), возвращает имя файла и размер. */
    suspend fun importImage(nb: Notebook, uri: Uri, keepOriginal: Boolean = false): ImportedImage? =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = ImageDecoder.createSource(appContext.contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    if (!keepOriginal) {
                        val longest = max(info.size.width, info.size.height)
                        if (longest > MAX_IMAGE) {
                            val k = MAX_IMAGE.toFloat() / longest
                            decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
                        }
                    }
                }
                importBitmap(nb, bmp)
            }.getOrNull()
        }

    suspend fun importBitmap(nb: Notebook, bmp: Bitmap): ImportedImage = withContext(Dispatchers.IO) {
        val dir = File(File(root, nb.id), "images").apply { mkdirs() }
        val png = bmp.hasAlpha()
        val name = UUID.randomUUID().toString() + if (png) ".png" else ".jpg"
        FileOutputStream(File(dir, name)).use { out ->
            bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
        }
        ImportedImage(name, bmp.width, bmp.height)
    }

    /** Загружает картинку для показа, уменьшая до maxSide. */
    fun decodeImage(nb: Notebook, fileName: String, maxSide: Int): Bitmap? {
        val f = imageFile(nb, fileName)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ---------- Внутреннее ----------

    private fun pageFile(nb: Notebook, pageId: String) = File(File(File(root, nb.id), "pages"), "$pageId.ncp")

    private fun newPageId() = UUID.randomUUID().toString().substring(0, 13)

    private inline fun atomicWrite(target: File, write: (java.io.OutputStream) -> Unit) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { fos ->
            BufferedOutputStream(fos).use { write(it) }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun writePage(out: java.io.OutputStream, data: PageData) {
        val d = DataOutputStream(out)
        d.writeInt(PAGE_MAGIC)
        d.writeInt(PAGE_VERSION)
        d.writeInt(data.strokes.size)
        for (s in data.strokes) {
            d.writeByte(s.pen.ordinal)
            d.writeInt(s.color)
            d.writeFloat(s.width)
            d.writeInt(s.points.size)
            for (v in s.points) d.writeFloat(v)
        }
        d.writeInt(data.images.size)
        for (im in data.images) {
            d.writeUTF(im.id); d.writeUTF(im.fileName)
            d.writeFloat(im.x); d.writeFloat(im.y); d.writeFloat(im.w); d.writeFloat(im.h)
            d.writeFloat(im.rotation); d.writeBoolean(im.locked)
        }
        d.flush()
    }

    private fun readPage(f: File): PageData {
        DataInputStream(BufferedInputStream(FileInputStream(f))).use { d ->
            if (d.readInt() != PAGE_MAGIC) return PageData()
            d.readInt() // версия
            val pens = PenType.entries
            val strokes = ArrayList<Stroke>()
            repeat(d.readInt()) {
                val pen = pens.getOrElse(d.readByte().toInt()) { PenType.BALLPOINT }
                val color = d.readInt()
                val width = d.readFloat()
                val n = d.readInt()
                val pts = FloatArray(n) { d.readFloat() }
                strokes += Stroke(pen, color, width, pts)
            }
            val images = ArrayList<ImageItem>()
            repeat(d.readInt()) {
                images += ImageItem(
                    id = d.readUTF(), fileName = d.readUTF(),
                    x = d.readFloat(), y = d.readFloat(), w = d.readFloat(), h = d.readFloat(),
                    rotation = d.readFloat(), locked = d.readBoolean(),
                )
            }
            return PageData(strokes, images)
        }
    }

    private fun toJson(nb: Notebook) = JSONObject().apply {
        put("id", nb.id); put("title", nb.title); put("template", nb.template.name)
        put("coverColor", nb.coverColor); put("coverMaterial", nb.coverMaterial.name)
        put("elastic", nb.elastic); put("ribbon", nb.ribbon)
        put("paperType", nb.paperType.name); put("paperColor", nb.paperColor)
        put("format", nb.format.name); put("orientation", nb.orientation.name)
        put("pages", JSONArray(nb.pages))
        put("pageDates", JSONObject().apply { nb.pageDates.forEach { (k, v) -> put(k, v) } })
        put("lastPage", nb.lastPage); put("favorite", nb.favorite)
        put("createdAt", nb.createdAt); put("updatedAt", nb.updatedAt)
        nb.deletedAt?.let { put("deletedAt", it) }
    }

    private fun readMeta(f: File): Notebook {
        val o = JSONObject(f.readText())
        val pagesArr = o.getJSONArray("pages")
        val pages = List(pagesArr.length()) { pagesArr.getString(it) }
        val datesObj = o.optJSONObject("pageDates") ?: JSONObject()
        val dates = HashMap<String, Long>()
        datesObj.keys().forEach { k -> dates[k] = datesObj.getLong(k) }
        return Notebook(
            id = o.getString("id"),
            title = o.getString("title"),
            template = enumOr(o.optString("template"), NotebookTemplate.NOTEBOOK),
            coverColor = o.getInt("coverColor"),
            coverMaterial = enumOr(o.optString("coverMaterial"), CoverMaterial.LEATHER),
            elastic = o.optBoolean("elastic", true),
            ribbon = o.optBoolean("ribbon", true),
            paperType = enumOr(o.optString("paperType"), PaperType.LINED),
            paperColor = o.getInt("paperColor"),
            format = enumOr(o.optString("format"), PageFormat.A5),
            orientation = enumOr(o.optString("orientation"), Orientation.PORTRAIT),
            pages = pages,
            pageDates = dates,
            lastPage = o.optInt("lastPage", 0).coerceIn(0, max(0, pages.size - 1)),
            favorite = o.optBoolean("favorite", false),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            deletedAt = if (o.has("deletedAt")) o.getLong("deletedAt") else null,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        runCatching { enumValueOf<E>(name ?: "") }.getOrDefault(default)

    data class ImportedImage(val fileName: String, val width: Int, val height: Int)

    companion object {
        private const val META = "meta.json"
        private const val PAGE_MAGIC = 0x4E435031 // "NCP1"
        private const val PAGE_VERSION = 1
        private const val MAX_IMAGE = 2560
        private const val TRASH_TTL = 30L * 24 * 60 * 60 * 1000
    }
}
