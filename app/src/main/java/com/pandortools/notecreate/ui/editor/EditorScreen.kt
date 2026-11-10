@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.pandortools.notecreate.ui.editor

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.PenType
import com.pandortools.notecreate.ink.EditorTool
import com.pandortools.notecreate.ink.EraserMode
import com.pandortools.notecreate.ink.PageView
import com.pandortools.notecreate.ink.SelectionInfo
import com.pandortools.notecreate.ui.library.NotebookSetupDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private data class LongPressInfo(val vx: Float, val vy: Float, val px: Float, val py: Float, val imageId: String?)

@Composable
fun EditorScreen(notebookId: String, onBack: () -> Unit) {
    val vm: EditorViewModel = viewModel(key = notebookId) { EditorViewModel(notebookId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = AppGraph.settings
    val settings by settingsStore.state.collectAsState()
    val nb by vm.notebook.collectAsState()
    val page by vm.page.collectAsState()
    val index by vm.index.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var tool by remember { mutableStateOf(EditorTool.PEN) }
    var pen by remember { mutableStateOf(PenType.BALLPOINT) }
    var color by remember { mutableIntStateOf(settings.favoriteColors.firstOrNull() ?: android.graphics.Color.BLACK) }
    var eraserMode by remember { mutableStateOf(EraserMode.AREA) }
    var eraserSize by remember { mutableFloatStateOf(8f) }
    var eraseOnlyHighlighter by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<SelectionInfo?>(null) }
    var longPress by remember { mutableStateOf<LongPressInfo?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerSlot by remember { mutableIntStateOf(-1) }
    var showWidth by remember { mutableStateOf(false) }
    var showImageMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showNotebookSettings by remember { mutableStateOf(false) }
    var clipboardChip by remember { mutableStateOf(false) }

    val pageView = remember { PageView(context) }
    val darkDesk = MaterialTheme.colorScheme.background.toArgb()

    // ---------- Вставка картинок ----------

    fun insertUri(uri: Uri, at: PointF?) {
        scope.launch {
            val img = vm.importImage(uri)
            if (img == null) { vm.showMessage("Не удалось открыть картинку"); return@launch }
            val c = at ?: pageView.visibleCenter()
            vm.placeImage(img, c.x, c.y)?.let { pageView.insertImage(it) }
        }
    }

    fun clipboardImageUri(): Uri? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val desc = cm.primaryClipDescription ?: return null
        if (!desc.hasMimeType("image/*")) return null
        val clip = cm.primaryClip ?: return null
        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { return it }
        return null
    }

    fun pasteFromClipboard(at: PointF?) {
        val uri = clipboardImageUri()
        if (uri == null) vm.showMessage("В буфере обмена нет картинки") else insertUri(uri, at)
        clipboardChip = false
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { insertUri(it, null) }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) insertUri(u, null)
    }
    fun openCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        cameraUri = u
        runCatching { takePhoto.launch(u) }.onFailure { vm.showMessage("Камера недоступна") }
    }

    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { vm.exportPdf(context, it) }
    }
    val exportPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { vm.exportPng(context, it) }
    }

    // Картинка из «Поделиться»
    val shared by AppGraph.pendingSharedImage.collectAsState()
    LaunchedEffect(shared, page?.id) {
        val u = shared
        if (u != null && page != null) {
            AppGraph.pendingSharedImage.value = null
            delay(300)
            insertUri(u, null)
        }
    }

    // Подсказка «вставить фото из буфера» при возвращении в приложение
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val d = cm.primaryClipDescription
                    val fresh = d != null && d.hasMimeType("image/*") &&
                        System.currentTimeMillis() - d.timestamp < 3 * 60 * 1000
                    clipboardChip = fresh
                }
                Lifecycle.Event.ON_PAUSE -> vm.flush()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); vm.flush() }
    }
    LaunchedEffect(clipboardChip) { if (clipboardChip) { delay(10_000); clipboardChip = false } }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    // ---------- Колбэки холста ----------
    LaunchedEffect(pageView) {
        pageView.onPageEdited = { vm.onPageEdited(it) }
        pageView.onHistoryChanged = { u, r -> canUndo = u; canRedo = r }
        pageView.onSelectionChanged = { selection = it }
        pageView.onLongPress = { vx, vy, px, py, id -> longPress = LongPressInfo(vx, vy, px, py, id) }
        pageView.turnProvider = { dir -> vm.turnTarget(dir) }
        pageView.onTurned = { dir, target -> vm.onTurned(dir, target) }
        pageView.imageLoader = { vm.loadImage(it) }
        pageView.onDropImage = { event, px, py ->
            val activity = context as? Activity
            val perms = activity?.requestDragAndDropPermissions(event)
            val uri = event.clipData?.let { cd -> (0 until cd.itemCount).firstNotNullOfOrNull { cd.getItemAt(it).uri } }
            if (uri != null) {
                scope.launch {
                    val img = vm.importImage(uri)
                    perms?.release()
                    if (img != null) vm.placeImage(img, px, py)?.let { pageView.insertImage(it) }
                }
                true
            } else { perms?.release(); false }
        }
    }

    val currentNb = nb
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (currentNb != null) {
            AndroidView(
                factory = { pageView },
                modifier = Modifier.fillMaxSize(),
                update = { v ->
                    v.notebook = currentNb
                    v.tool = tool
                    v.pen = pen
                    v.color = color
                    v.penWidth = settings.penWidths[pen] ?: pen.defaultWidth
                    v.eraserMode = eraserMode
                    v.eraserSize = eraserSize
                    v.eraseOnlyHighlighter = eraseOnlyHighlighter
                    v.fingerDraw = settings.fingerDraw
                    v.smoothing = settings.smoothing
                    v.turnStyle = settings.pageTurn
                    v.threeFingerAction = settings.threeFingerAction
                    v.deskColor = darkDesk
                    page?.let { p -> v.setPage(p.id, p.data, p.date) }
                },
            )
        }

        // ---------- Верхняя панель ----------
        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
            ) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    Bar()
                    PenType.entries.forEach { p ->
                        ToolButton(
                            icon = penIcon(p), label = p.title,
                            selected = tool == EditorTool.PEN && pen == p,
                        ) { tool = EditorTool.PEN; pen = p }
                    }
                    ToolButton(ToolIcons.Eraser, "Ластик", tool == EditorTool.ERASER) { tool = EditorTool.ERASER }
                    ToolButton(ToolIcons.Lasso, "Лассо", tool == EditorTool.LASSO) { tool = EditorTool.LASSO }
                    Bar()
                    settings.favoriteColors.take(6).forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    if (c == color) 3.dp else 1.dp,
                                    if (c == color) MaterialTheme.colorScheme.secondary else Color.Black.copy(alpha = 0.2f),
                                    CircleShape,
                                )
                                .combinedClickable(
                                    onClick = { color = c; if (tool != EditorTool.PEN) tool = EditorTool.PEN },
                                    onLongClick = { pickerSlot = i; showPicker = true },
                                ),
                        )
                    }
                    IconButton(onClick = { pickerSlot = -1; showPicker = true }) { Icon(Icons.Filled.Palette, "Палитра") }
                    Box {
                        IconButton(onClick = { showWidth = true }) { Icon(Icons.Filled.LineWeight, "Толщина") }
                        DropdownMenu(expanded = showWidth, onDismissRequest = { showWidth = false }) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(280.dp)) {
                                if (tool == EditorTool.ERASER) {
                                    Text("Ластик · стирает только под кончиком", style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(eraserMode == EraserMode.AREA, { eraserMode = EraserMode.AREA }, { Text("Частично") })
                                        FilterChip(eraserMode == EraserMode.STROKE, { eraserMode = EraserMode.STROKE }, { Text("Весь штрих") })
                                    }
                                    Text("Размер: ${eraserSize.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                                    Slider(eraserSize, { eraserSize = it }, valueRange = 2f..40f)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Только маркер", Modifier.weight(1f))
                                        Switch(eraseOnlyHighlighter, { eraseOnlyHighlighter = it })
                                    }
                                } else {
                                    val w = settings.penWidths[pen] ?: pen.defaultWidth
                                    Text("${pen.title}: толщина ${"%.1f".format(w)}", style = MaterialTheme.typography.titleSmall)
                                    val range = if (pen == PenType.HIGHLIGHTER) 4f..30f else 0.4f..12f
                                    Slider(w, { v -> settingsStore.update { s -> s.copy(penWidths = s.penWidths + (pen to v)) } }, valueRange = range)
                                    Box(Modifier.height(36.dp).width(260.dp), contentAlignment = Alignment.Center) {
                                        Box(
                                            Modifier.width(220.dp).height((w * 1.4f).coerceIn(1f, 36f).dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Color(color).copy(alpha = if (pen == PenType.HIGHLIGHTER) 0.4f else 1f))
                                        )
                                    }
                                    Text("Сглаживание линии: ${(settings.smoothing * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                                    Slider(settings.smoothing, { v -> settingsStore.update { it.copy(smoothing = v) } }, valueRange = 0f..0.9f)
                                }
                            }
                        }
                    }
                    Bar()
                    Box {
                        IconButton(onClick = { showImageMenu = true }) { Icon(Icons.Filled.Image, "Картинка") }
                        DropdownMenu(expanded = showImageMenu, onDismissRequest = { showImageMenu = false }) {
                            DropdownMenuItem(text = { Text("Из галереи") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                                onClick = {
                                    showImageMenu = false
                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                            DropdownMenuItem(text = { Text("Снять камерой") }, leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                                onClick = { showImageMenu = false; openCamera() })
                            DropdownMenuItem(text = { Text("Вставить из буфера") }, leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                onClick = { showImageMenu = false; pasteFromClipboard(null) })
                        }
                    }
                    IconButton(onClick = { pageView.undo() }, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Отменить") }
                    IconButton(onClick = { pageView.redo() }, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Повторить") }
                    Bar()
                    IconButton(onClick = { vm.flush(); showPages = true }) { Icon(Icons.Filled.GridView, "Страницы") }
                    Box {
                        IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(text = { Text("Новая страница") }, onClick = { showMore = false; vm.addPageAfterCurrent() })
                            DropdownMenuItem(text = { Text("Дублировать страницу") }, onClick = { showMore = false; vm.duplicatePage(index) })
                            DropdownMenuItem(text = { Text("Удалить страницу") }, onClick = { showMore = false; vm.deletePage(index) })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Экспорт тетради в PDF") }, onClick = {
                                showMore = false; exportPdf.launch((currentNb?.title ?: "NoteCreate") + ".pdf")
                            })
                            DropdownMenuItem(text = { Text("Экспорт страницы в PNG") }, onClick = {
                                showMore = false; exportPng.launch((currentNb?.title ?: "NoteCreate") + " — стр. ${index + 1}.png")
                            })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Обложка и бумага") }, onClick = { showMore = false; showNotebookSettings = true })
                            DropdownMenuItem(text = { Text("Сбросить масштаб") }, onClick = { showMore = false; pageView.resetZoom() })
                            DropdownMenuItem(
                                text = { Text("Рисовать пальцем") },
                                trailingIcon = { Checkbox(settings.fingerDraw, null) },
                                onClick = { settingsStore.update { it.copy(fingerDraw = !it.fingerDraw) } },
                            )
                        }
                    }
                }
            }

            // ---------- Панель выделения ----------
            val sel = selection
            if (sel != null) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 4.dp) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(selectionLabel(sel), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                        if (sel.strokes > 0) IconButton(onClick = { pageView.recolorSelection(color) }) {
                            Icon(Icons.Filled.FormatColorFill, "Перекрасить в текущий цвет")
                        }
                        IconButton(onClick = { pageView.duplicateSelection() }) { Icon(Icons.Filled.ContentCopy, "Дублировать") }
                        if (sel.images > 0) {
                            IconButton(onClick = { pageView.bringSelectionToFront() }) { Icon(Icons.Filled.FlipToFront, "Наверх") }
                            IconButton(onClick = { pageView.sendSelectionToBack() }) { Icon(Icons.Filled.FlipToBack, "Вниз") }
                        }
                        if (sel.singleImageLocked != null) IconButton(onClick = { pageView.toggleLockSelection() }) {
                            Icon(Icons.Filled.Lock, "Закрепить, чтобы писать поверх")
                        }
                        IconButton(onClick = { pageView.deleteSelection() }) { Icon(Icons.Filled.Delete, "Удалить") }
                        IconButton(onClick = { pageView.clearSelection() }) { Icon(Icons.Filled.Check, "Готово") }
                    }
                }
            }

            if (clipboardChip && sel == null) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = { pasteFromClipboard(null) },
                    label = { Text("Вставить фото из буфера") },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, "Скрыть", Modifier.size(AssistChipDefaults.IconSize).clip(CircleShape)
                            .combinedClickable(onClick = { clipboardChip = false }))
                    },
                )
            }
        }

        // ---------- Номер страницы ----------
        if (currentNb != null) {
            Surface(
                modifier = Modifier
                    .align(if (settings.leftHanded) Alignment.BottomEnd else Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.goTo(index - 1) }, enabled = index > 0) { Icon(Icons.Filled.ChevronLeft, "Назад") }
                    Text("${index + 1} / ${currentNb.pages.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { vm.goTo(index + 1) }, enabled = index < currentNb.pages.size - 1) {
                        Icon(Icons.Filled.ChevronRight, "Вперёд")
                    }
                    IconButton(onClick = { vm.addPageAfterCurrent() }) { Icon(Icons.Filled.Add, "Новая страница") }
                }
            }
        }

        // ---------- Меню долгого нажатия ----------
        longPress?.let { lp ->
            Box(Modifier.offset { IntOffset(lp.vx.roundToInt(), lp.vy.roundToInt()) }.size(1.dp)) {
                DropdownMenu(expanded = true, onDismissRequest = { longPress = null }) {
                    val img = lp.imageId?.let { id -> page?.data?.images?.firstOrNull { it.id == id } }
                    if (img != null) {
                        if (!img.locked) DropdownMenuItem(text = { Text("Выделить картинку") },
                            onClick = { longPress = null; pageView.selectImage(img.id) })
                        DropdownMenuItem(
                            text = { Text(if (img.locked) "Открепить картинку" else "Закрепить картинку") },
                            leadingIcon = { Icon(if (img.locked) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                            onClick = { longPress = null; pageView.setImageLocked(img.id, !img.locked) })
                        DropdownMenuItem(text = { Text("Удалить картинку") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { longPress = null; pageView.deleteImage(img.id) })
                        HorizontalDivider()
                    }
                    DropdownMenuItem(text = { Text("Вставить") }, leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                        onClick = { longPress = null; pasteFromClipboard(PointF(lp.px, lp.py)) })
                    DropdownMenuItem(text = { Text("Картинка из галереи") }, leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                        onClick = {
                            longPress = null
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    // ---------- Диалоги ----------
    if (showPicker) {
        val initial = if (pickerSlot >= 0) settings.favoriteColors.getOrElse(pickerSlot) { color } else color
        ColorPickerDialog(initial = initial, recent = settings.recentColors, onDismiss = { showPicker = false }) { c ->
            showPicker = false
            color = c
            if (tool != EditorTool.PEN) tool = EditorTool.PEN
            settingsStore.pushRecentColor(c)
            if (pickerSlot >= 0) settingsStore.update { s ->
                s.copy(favoriteColors = s.favoriteColors.toMutableList().also { l -> if (pickerSlot < l.size) l[pickerSlot] = c })
            }
        }
    }
    if (showPages && currentNb != null) {
        PageOverviewDialog(
            nb = currentNb, current = index,
            thumbnail = { i -> vm.thumbnail(i, 320) },
            onDismiss = { showPages = false },
            onOpen = { showPages = false; vm.goTo(it) },
            onAddAt = { showPages = false; vm.addPageAt(it) },
            onDuplicate = { vm.duplicatePage(it) },
            onMove = { a, b -> vm.movePage(a, b) },
            onDelete = { vm.deletePage(it) },
        )
    }
    if (showNotebookSettings && currentNb != null) {
        NotebookSetupDialog(existing = currentNb, defaults = settings, onDismiss = { showNotebookSettings = false }) { spec ->
            showNotebookSettings = false
            vm.applySpec(spec)
        }
    }
}

@Composable
private fun Bar() {
    VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp))
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun penIcon(p: PenType): ImageVector = when (p) {
    PenType.BALLPOINT -> ToolIcons.Ballpoint
    PenType.FOUNTAIN -> ToolIcons.Fountain
    PenType.PENCIL -> ToolIcons.Pencil
    PenType.HIGHLIGHTER -> ToolIcons.Highlighter
}

private fun selectionLabel(s: SelectionInfo): String {
    val parts = ArrayList<String>()
    if (s.strokes > 0) parts += "штрихов: ${s.strokes}"
    if (s.images > 0) parts += "картинок: ${s.images}"
    return parts.joinToString(", ")
}
