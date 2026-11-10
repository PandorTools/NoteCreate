@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.pandortools.notecreate.ui.editor

import android.app.Activity
import android.content.ContextWrapper
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.PageLayers
import com.pandortools.notecreate.data.PageLayer
import com.pandortools.notecreate.data.PageTemplate
import com.pandortools.notecreate.data.TextBlock
import com.pandortools.notecreate.data.AppSettings
import com.pandortools.notecreate.data.PenType
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.pandortools.notecreate.data.SettingsState
import com.pandortools.notecreate.ink.EditorTool
import com.pandortools.notecreate.ink.EraserMode
import com.pandortools.notecreate.ink.PageView
import com.pandortools.notecreate.ink.SelectionInfo
import com.pandortools.notecreate.ui.library.NotebookSetupDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class LongPressInfo(val vx: Float, val vy: Float, val px: Float, val py: Float, val imageId: String?)

/** Какая всплывающая панель открыта у панели инструментов. */
private enum class ToolPanel { NONE, PEN, ERASER, LASSO, IMAGE, MORE }

private val PEN_PRESETS = listOf(0.8f, 1.2f, 1.8f, 2.6f, 4f)
private val MARKER_PRESETS = listOf(6f, 10f, 14f, 20f, 28f)

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
    var rulerVisible by remember { mutableStateOf(false) }
    var activeLayerId by remember { mutableStateOf(PageLayers.DRAWING) }
    var pen by remember { mutableStateOf(PenType.BALLPOINT) }
    var color by remember { mutableIntStateOf(settings.favoriteColors.firstOrNull() ?: android.graphics.Color.BLACK) }
    var eraserMode by remember { mutableStateOf(EraserMode.AREA) }
    var eraserSize by remember { mutableFloatStateOf(8f) }
    var eraseOnlyHighlighter by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<SelectionInfo?>(null) }
    var longPress by remember { mutableStateOf<LongPressInfo?>(null) }
    var panel by remember { mutableStateOf(ToolPanel.NONE) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerSlot by remember { mutableIntStateOf(-1) }
    var showPages by remember { mutableStateOf(false) }
    var showNotebookSettings by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLayerPanel by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var clipboardChip by remember { mutableStateOf(false) }

    val pageView = remember { PageView(context) }
    val deskColor = MaterialTheme.colorScheme.surfaceContainer.toArgb()

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
    fun openGallery() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
    val importPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importPdf(it) }
    }
    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val documentScanner = remember(scannerOptions) { GmsDocumentScanning.getClient(scannerOptions) }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scan?.pdf?.uri
            if (pdfUri != null) vm.importPdf(pdfUri)
            else scan?.pages?.firstOrNull()?.imageUri?.let { insertUri(it, null) }
        }
    }
    fun openDocumentScanner() {
        val activity = context.findActivity()
        if (activity == null) { vm.showMessage("Сканер недоступен") ; return }
        documentScanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scannerLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { vm.showMessage("Не удалось открыть сканер документов") }
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
                    clipboardChip = d != null && d.hasMimeType("image/*") &&
                        System.currentTimeMillis() - d.timestamp < 3 * 60 * 1000
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

    /** Нажатие на инструмент: первый раз выбирает, повторно — открывает его настройки. */
    fun onToolTap(t: EditorTool, p: PenType? = null) {
        val already = tool == t && (p == null || pen == p)
        if (already) {
            panel = when (t) {
                EditorTool.PEN -> if (panel == ToolPanel.PEN) ToolPanel.NONE else ToolPanel.PEN
                EditorTool.ERASER -> if (panel == ToolPanel.ERASER) ToolPanel.NONE else ToolPanel.ERASER
                EditorTool.LASSO -> if (panel == ToolPanel.LASSO) ToolPanel.NONE else ToolPanel.LASSO
            }
        } else {
            tool = t
            if (p != null) pen = p
            panel = ToolPanel.NONE
        }
    }

    fun pickColor(c: Int) {
        color = c
        if (tool != EditorTool.PEN) tool = EditorTool.PEN
    }

    val currentNb = nb
    val dateText = page?.date?.let { SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(it)) }

    // ---------- Содержимое панели инструментов ----------
    val toolsPill: @Composable () -> Unit = {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PenType.entries.forEach { p ->
                    Box {
                        ToolButton(
                            icon = penIcon(p), label = p.title,
                            selected = tool == EditorTool.PEN && pen == p,
                            indicator = if (tool == EditorTool.PEN && pen == p) Color(color) else null,
                        ) { onToolTap(EditorTool.PEN, p) }
                        if (panel == ToolPanel.PEN && tool == EditorTool.PEN && pen == p) {
                            ToolPopup(onDismiss = { panel = ToolPanel.NONE }) {
                                PenOptions(p, color, settings, settingsStore,
                                    onColor = ::pickColor,
                                    onEditFavorite = { slot -> pickerSlot = slot; showPicker = true; panel = ToolPanel.NONE },
                                    onMoreColors = { pickerSlot = -1; showPicker = true; panel = ToolPanel.NONE })
                            }
                        }
                    }
                }
                Box {
                    ToolButton(ToolIcons.Eraser, "Ластик", tool == EditorTool.ERASER) { onToolTap(EditorTool.ERASER) }
                    if (panel == ToolPanel.ERASER) {
                        ToolPopup(onDismiss = { panel = ToolPanel.NONE }) {
                            EraserOptions(eraserMode, { eraserMode = it }, eraserSize, { eraserSize = it },
                                eraseOnlyHighlighter, { eraseOnlyHighlighter = it })
                        }
                    }
                }
                Box {
                    ToolButton(ToolIcons.Lasso, "Лассо", tool == EditorTool.LASSO) { onToolTap(EditorTool.LASSO) }
                    if (panel == ToolPanel.LASSO) {
                        ToolPopup(onDismiss = { panel = ToolPanel.NONE }) { LassoOptions() }
                    }
                }
                IconButton(onClick = { rulerVisible = !rulerVisible }) {
                    Icon(Icons.Outlined.Straighten, if (rulerVisible) "Скрыть линейку" else "Показать линейку",
                        tint = if (rulerVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                PillDivider()
                Box {
                    ToolButton(Icons.Outlined.Image, "Вставить картинку", panel == ToolPanel.IMAGE) {
                        panel = if (panel == ToolPanel.IMAGE) ToolPanel.NONE else ToolPanel.IMAGE
                    }
                    DropdownMenu(expanded = panel == ToolPanel.IMAGE, onDismissRequest = { panel = ToolPanel.NONE }) {
                        MenuItem(Icons.Outlined.PhotoLibrary, "Из галереи") { panel = ToolPanel.NONE; openGallery() }
                        MenuItem(Icons.Outlined.PhotoCamera, "Снять камерой") { panel = ToolPanel.NONE; openCamera() }
                        MenuItem(Icons.Outlined.ContentPaste, "Вставить из буфера") { panel = ToolPanel.NONE; pasteFromClipboard(null) }
                    }
                }
                PillDivider()
                // Текущий цвет и быстрые цвета
                CurrentColorButton(Color(color)) { pickerSlot = -1; showPicker = true }
                settings.favoriteColors.filter { it != color }.take(4).forEach { c ->
                    val slot = settings.favoriteColors.indexOf(c)
                    QuickColor(Color(c), onClick = { pickColor(c) }, onLongClick = { pickerSlot = slot; showPicker = true })
                }
            }
        }
    }

    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pageView.undo() }, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Отменить") }
            IconButton(onClick = { pageView.redo() }, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Повторить") }
            IconButton(onClick = { vm.flush(); showPages = true }) { Icon(Icons.Outlined.GridView, "Все страницы") }
            Box {
                IconButton(onClick = { panel = if (panel == ToolPanel.MORE) ToolPanel.NONE else ToolPanel.MORE }) {
                    Icon(Icons.Filled.MoreVert, "Ещё")
                }
                DropdownMenu(expanded = panel == ToolPanel.MORE, onDismissRequest = { panel = ToolPanel.NONE }) {
                    MenuItem(Icons.Outlined.NoteAdd, "Новая страница") { panel = ToolPanel.NONE; vm.addPageAfterCurrent() }
                    MenuItem(Icons.Outlined.ContentCopy, "Дублировать страницу") { panel = ToolPanel.NONE; vm.duplicatePage(index) }
                    MenuItem(Icons.Outlined.Delete, "Удалить страницу") { panel = ToolPanel.NONE; vm.deletePage(index) }
                    HorizontalDivider()
                    MenuItem(Icons.Outlined.CalendarToday, "Дата страницы") { panel = ToolPanel.NONE; showDatePicker = true }
                    val currentPageId = currentNb?.pages?.getOrNull(index)
                    val isBookmarked = currentPageId != null && currentPageId in (currentNb?.bookmarks ?: emptySet())
                    MenuItem(if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        if (isBookmarked) "Убрать закладку" else "Добавить закладку") {
                        panel = ToolPanel.NONE; vm.toggleBookmark(index)
                    }
                    MenuItem(Icons.Outlined.Tune, "Обложка и бумага") { panel = ToolPanel.NONE; showNotebookSettings = true }
                    MenuItem(Icons.Outlined.ZoomOutMap, "Вписать страницу") { panel = ToolPanel.NONE; pageView.resetZoom() }
                    MenuItem(Icons.Outlined.Straighten, if (rulerVisible) "Скрыть линейку" else "Показать линейку") {
                        panel = ToolPanel.NONE; rulerVisible = !rulerVisible
                    }
                    MenuItem(Icons.Outlined.Layers, "Слои страницы") { panel = ToolPanel.NONE; showLayerPanel = true }
                    MenuItem(Icons.Outlined.TextFields, "Добавить текстовый блок") { panel = ToolPanel.NONE; showTextDialog = true }
                    MenuItem(Icons.Outlined.Tune, "Шаблон страницы") { panel = ToolPanel.NONE; showTemplatePicker = true }
                    HorizontalDivider()
                    MenuItem(Icons.Outlined.PictureAsPdf, "Импортировать PDF") {
                        panel = ToolPanel.NONE; importPdf.launch(arrayOf("application/pdf"))
                    }
                    MenuItem(Icons.Outlined.PhotoCamera, "Сканировать документ") {
                        panel = ToolPanel.NONE; openDocumentScanner()
                    }
                    MenuItem(Icons.Outlined.PictureAsPdf, "Экспорт тетради в PDF") {
                        panel = ToolPanel.NONE; exportPdf.launch((currentNb?.title ?: "NoteCreate") + ".pdf")
                    }
                    MenuItem(Icons.Outlined.Image, "Экспорт страницы в PNG") {
                        panel = ToolPanel.NONE; exportPng.launch((currentNb?.title ?: "NoteCreate") + " — стр. ${index + 1}.png")
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Рисовать пальцем") },
                        leadingIcon = { Icon(Icons.Outlined.TouchApp, null) },
                        trailingIcon = { Switch(checked = settings.fingerDraw, onCheckedChange = null) },
                        onClick = { settingsStore.update { it.copy(fingerDraw = !it.fingerDraw) } },
                    )
                }
            }
        }
    }

    val titleBlock: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(currentNb?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDatePicker = true }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarToday, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(dateText ?: "Указать дату", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        // ---------- Верхняя панель ----------
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding()) {
                // At medium tablet widths the toolbar used to compete with the title and actions
                // in one Row, pushing its controls outside the visible window. Keep it on its
                // own horizontally scrollable row until there is room for all three sections.
                if (maxWidth >= 1200.dp) {
                    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(250.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "К библиотеке") }
                            titleBlock(Modifier.weight(1f))
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.horizontalScroll(rememberScrollState())) { toolsPill() }
                        }
                        actions()
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.flush(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "К библиотеке") }
                            titleBlock(Modifier.weight(1f))
                            actions()
                        }
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.horizontalScroll(rememberScrollState())) { toolsPill() }
                        }
                    }
                }
            }
        }

        // ---------- Холст ----------
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (currentNb != null) {
                AndroidView(
                    factory = { pageView },
                    modifier = Modifier.fillMaxSize(),
                    update = { v ->
                        v.notebook = currentNb
                        v.tool = tool
                        v.pen = pen
                        v.color = if (pen == PenType.HIGHLIGHTER) Color(color).copy(alpha = settings.highlighterOpacity).toArgb() else color
                        v.penWidth = settings.penWidths[pen] ?: pen.defaultWidth
                        v.eraserMode = eraserMode
                        v.eraserSize = eraserSize
                        v.eraseOnlyHighlighter = eraseOnlyHighlighter
                        v.fingerDraw = settings.fingerDraw
                        v.smoothing = settings.smoothing
                        v.turnStyle = settings.pageTurn
                        v.threeFingerAction = settings.threeFingerAction
                        v.rulerVisible = rulerVisible
                        v.activeLayerId = activeLayerId
                        v.deskColor = deskColor
                        page?.let { p -> v.setPage(p.id, p.data, p.date) }
                    },
                )
            }

            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val sel = selection
                if (sel != null) {
                    SelectionBar(
                        info = sel,
                        onRecolor = { pageView.recolorSelection(color) },
                        onDuplicate = { pageView.duplicateSelection() },
                        onFront = { pageView.bringSelectionToFront() },
                        onBack = { pageView.sendSelectionToBack() },
                        onLock = { pageView.toggleLockSelection() },
                        onDelete = { pageView.deleteSelection() },
                        onDone = { pageView.clearSelection() },
                    )
                } else if (clipboardChip) {
                    AssistChip(
                        onClick = { pasteFromClipboard(null) },
                        label = { Text("Вставить фото из буфера") },
                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, "Скрыть", Modifier.size(AssistChipDefaults.IconSize).clip(CircleShape)
                                .clickable { clipboardChip = false })
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = AssistChipDefaults.assistChipElevation(elevation = 3.dp),
                    )
                }
            }

            // ---------- Номер страницы ----------
            if (currentNb != null) {
                PageIndicator(
                    index = index,
                    count = currentNb.pages.size,
                    onPrev = { vm.goTo(index - 1) },
                    onNext = { vm.goTo(index + 1) },
                    onOpenAll = { vm.flush(); showPages = true },
                    onAdd = { vm.addPageAfterCurrent() },
                    modifier = Modifier
                        // Подальше от ладони: правше — слева, левше — справа.
                        .align(if (settings.leftHanded) Alignment.BottomEnd else Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }

            // ---------- Меню долгого нажатия ----------
            longPress?.let { lp ->
                Box(Modifier.offset { IntOffset(lp.vx.roundToInt(), lp.vy.roundToInt()) }.size(1.dp)) {
                    DropdownMenu(expanded = true, onDismissRequest = { longPress = null }) {
                        val img = lp.imageId?.let { id -> page?.data?.images?.firstOrNull { it.id == id } }
                        if (img != null) {
                            if (!img.locked) MenuItem(Icons.Outlined.OpenWith, "Выделить картинку") {
                                longPress = null; pageView.selectImage(img.id)
                            }
                            MenuItem(if (img.locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                                if (img.locked) "Открепить картинку" else "Закрепить картинку") {
                                longPress = null; pageView.setImageLocked(img.id, !img.locked)
                            }
                            MenuItem(Icons.Outlined.Delete, "Удалить картинку") { longPress = null; pageView.deleteImage(img.id) }
                            HorizontalDivider()
                        }
                        MenuItem(Icons.Outlined.ContentPaste, "Вставить") { longPress = null; pasteFromClipboard(PointF(lp.px, lp.py)) }
                        MenuItem(Icons.Outlined.PhotoLibrary, "Картинка из галереи") { longPress = null; openGallery() }
                    }
                }
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
        }
    }

    // ---------- Диалоги ----------
    if (showPicker) {
        val initial = if (pickerSlot >= 0) settings.favoriteColors.getOrElse(pickerSlot) { color } else color
        ColorPickerDialog(initial = initial, recent = settings.recentColors, onDismiss = { showPicker = false }) { c ->
            showPicker = false
            pickColor(c)
            settingsStore.pushRecentColor(c)
            if (pickerSlot >= 0) settingsStore.update { s ->
                s.copy(favoriteColors = s.favoriteColors.toMutableList().also { l -> if (pickerSlot < l.size) l[pickerSlot] = c })
            }
        }
    }
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = page?.date ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let(vm::setPageDate)
                    showDatePicker = false
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) { DatePicker(state = dateState) }
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
    if (showTextDialog) {
        TextBlockDialog(color = color, onDismiss = { showTextDialog = false }) { text, size, font ->
            val center = pageView.visibleCenter()
            pageView.addTextBlock(TextBlock(java.util.UUID.randomUUID().toString(), text, center.x, center.y,
                size, color, font, activeLayerId))
            showTextDialog = false
        }
    }
    if (showLayerPanel && page != null) {
        LayerPanelDialog(
            layers = page!!.data.layers,
            activeLayerId = activeLayerId,
            onDismiss = { showLayerPanel = false },
            onActive = { activeLayerId = it },
            onUpdate = pageView::updateLayer,
        )
    }
    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text("Шаблон страницы") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageTemplate.entries.forEach { template ->
                        FilterChip(
                            selected = page?.data?.template == template,
                            onClick = { pageView.edit { it.copy(template = template) }; showTemplatePicker = false },
                            label = { Text(template.title) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTemplatePicker = false }) { Text("Закрыть") } },
        )
    }
}

// =====================================================================
// Кнопки панели
// =====================================================================

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    indicator: Color? = null,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(22.dp).offset(y = if (indicator != null) (-3).dp else 0.dp))
        if (indicator != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(width = 16.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(indicator),
            )
        }
    }
}

@Composable
private fun PillDivider() {
    VerticalDivider(Modifier.height(26.dp).padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CurrentColorButton(color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(34.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun QuickColor(color: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun TextBlockDialog(
    color: Int,
    onDismiss: () -> Unit,
    onAdd: (String, Float, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableFloatStateOf(18f) }
    var font by remember { mutableStateOf("sans") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Текстовый блок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Напечатай текст") }, minLines = 3)
                Text("Шрифт", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("sans" to "Без засечек", "serif" to "С засечками", "mono" to "Моно").forEach { (id, label) ->
                        FilterChip(font == id, { font = id }, { Text(label) })
                    }
                }
                Text("Размер: ${size.toInt()} pt", style = MaterialTheme.typography.bodySmall)
                Slider(value = size, onValueChange = { size = it }, valueRange = 10f..40f)
                Text(text, style = MaterialTheme.typography.bodyLarge, color = Color(color), maxLines = 4)
                Text("Блок появится в центре листа; затем его можно распечатать или экспортировать вместе со страницей.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text.trim(), size, font) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun LayerPanelDialog(
    layers: List<PageLayer>,
    activeLayerId: String,
    onDismiss: () -> Unit,
    onActive: (String) -> Unit,
    onUpdate: (PageLayer) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Слои страницы") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выбери слой для письма. Скрытые слои не отображаются, заблокированные защищены от изменений.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                layers.forEach { layer ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (layer.id != PageLayers.BACKGROUND) {
                                androidx.compose.material3.RadioButton(selected = activeLayerId == layer.id, onClick = { onActive(layer.id) })
                            } else Spacer(Modifier.size(48.dp))
                            Text(layer.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onUpdate(layer.copy(visible = !layer.visible)) }) {
                                Icon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    if (layer.visible) "Скрыть слой" else "Показать слой")
                            }
                            IconButton(onClick = { onUpdate(layer.copy(locked = !layer.locked)) }) {
                                Icon(if (layer.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                    if (layer.locked) "Разблокировать слой" else "Заблокировать слой")
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Новый слой") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            val layer = PageLayer(java.util.UUID.randomUUID().toString(), name)
                            onUpdate(layer); onActive(layer.id); newName = ""
                        }
                    }, enabled = newName.isNotBlank()) { Icon(Icons.Filled.Add, "Создать слой") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

// =====================================================================
// Всплывающие настройки инструментов
// =====================================================================

/** Открывает карточку под кнопкой и не даёт ей выйти за край экрана. */
private class BelowAnchorPositionProvider(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
        var y = anchorBounds.bottom + gap
        if (y + popupContentSize.height > windowSize.height - margin) {
            y = (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        }
        return IntOffset(x, y)
    }
}

@Composable
private fun ToolPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val provider = remember(density) {
        with(density) { BelowAnchorPositionProvider(10.dp.roundToPx(), 12.dp.roundToPx()) }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.width(320.dp),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

@Composable
private fun PopupHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** Подпись параметра: иконка, название и значение справа. */
@Composable
private fun OptionLabel(icon: ImageVector, title: String, value: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PenOptions(
    pen: PenType,
    color: Int,
    settings: SettingsState,
    store: AppSettings,
    onColor: (Int) -> Unit,
    onEditFavorite: (Int) -> Unit,
    onMoreColors: () -> Unit,
) {
    val marker = pen == PenType.HIGHLIGHTER
    val width = settings.penWidths[pen] ?: pen.defaultWidth
    val presets = if (marker) MARKER_PRESETS else PEN_PRESETS
    val range = if (marker) 4f..30f else 0.4f..12f
    fun setWidth(v: Float) = store.update { it.copy(penWidths = it.penWidths + (pen to v)) }

    PopupHeader(penIcon(pen), pen.title)
    StrokePreview(pen, Color(color), width, if (marker) settings.highlighterOpacity else 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OptionLabel(Icons.Outlined.LineWeight, "Толщина", "%.1f".format(width))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            presets.forEachIndexed { i, preset ->
                val active = abs(width - preset) < 0.05f
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { setWidth(preset) },
                    contentAlignment = Alignment.Center,
                ) {
                    val d = (6 + i * 4).dp
                    Box(Modifier.size(d).clip(CircleShape).background(Color(color).copy(alpha = if (marker) 0.5f else 1f)))
                }
            }
        }
        Slider(value = width.coerceIn(range.start, range.endInclusive), onValueChange = ::setWidth, valueRange = range)
    }

    if (marker) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OptionLabel(Icons.Outlined.Opacity, "Прозрачность", "${(settings.highlighterOpacity * 100).roundToInt()}%")
            Slider(settings.highlighterOpacity, { v -> store.update { it.copy(highlighterOpacity = v) } }, valueRange = 0.08f..0.8f)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OptionLabel(Icons.Outlined.Gesture, "Сглаживание", "${(settings.smoothing * 100).roundToInt()}%")
        Slider(settings.smoothing, { v -> store.update { it.copy(smoothing = v) } }, valueRange = 0f..0.9f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionLabel(Icons.Outlined.Palette, "Цвет")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.favoriteColors.forEachIndexed { slot, c ->
                val active = c == color
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (active) 3.dp else 1.dp,
                            if (active) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.15f), CircleShape)
                        .combinedClickable(onClick = { onColor(c) }, onLongClick = { onEditFavorite(slot) }),
                )
            }
            FilledTonalIconButton(onClick = onMoreColors, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Add, "Другой цвет", Modifier.size(18.dp))
            }
        }
        Text("Удерживай кружок, чтобы заменить цвет", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Образец линии выбранным пером. */
@Composable
private fun StrokePreview(pen: PenType, color: Color, width: Float, alpha: Float) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val path = Path()
            val w = size.width - 48f
            for (i in 0..60) {
                val t = i / 60f
                val x = 24f + w * t
                val y = size.height / 2f + sin(t * 2 * PI).toFloat() * size.height * 0.22f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val px = (width * density * 0.9f).coerceIn(1.5f, size.height * 0.7f)
            drawPath(path, color.copy(alpha = alpha), style = Stroke(width = px, cap = if (pen == PenType.HIGHLIGHTER) StrokeCap.Square else StrokeCap.Round))
        }
    }
}

@Composable
private fun EraserOptions(
    mode: EraserMode,
    onMode: (EraserMode) -> Unit,
    size: Float,
    onSize: (Float) -> Unit,
    onlyHighlighter: Boolean,
    onOnlyHighlighter: (Boolean) -> Unit,
) {
    PopupHeader(ToolIcons.Eraser, "Ластик")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EraserModeTile(ToolIcons.Eraser, "Частично", mode == EraserMode.AREA, Modifier.weight(1f)) { onMode(EraserMode.AREA) }
        EraserModeTile(Icons.Outlined.BlurOn, "Мягкий", mode == EraserMode.SOFT, Modifier.weight(1f)) { onMode(EraserMode.SOFT) }
        EraserModeTile(Icons.Outlined.DeleteSweep, "Штрих", mode == EraserMode.STROKE, Modifier.weight(1f)) { onMode(EraserMode.STROKE) }
    }
    Text(
        when (mode) {
            EraserMode.AREA -> "Стирает только то, что под ластиком."
            EraserMode.SOFT -> "Делает линию бледнее. Проведи ещё раз, чтобы осветлить сильнее."
            EraserMode.STROKE -> "Удаляет линию целиком одним касанием."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OptionLabel(Icons.Outlined.LineWeight, "Размер", size.roundToInt().toString())
        Slider(size, onSize, valueRange = 2f..40f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ToolIcons.Highlighter, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("Стирать только маркер", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = onlyHighlighter, onCheckedChange = onOnlyHighlighter)
    }
}

@Composable
private fun EraserModeTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp),
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LassoOptions() {
    PopupHeader(ToolIcons.Lasso, "Лассо")
    LassoTip(ToolIcons.Lasso, "Обведи штрихи или картинку")
    LassoTip(Icons.Outlined.OpenWith, "Тяни выделение, чтобы переместить")
    LassoTip(Icons.Outlined.AspectRatio, "Нижний кружок меняет размер")
    LassoTip(Icons.Outlined.RotateRight, "Верхний кружок поворачивает")
}

@Composable
private fun LassoTip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// =====================================================================
// Панель выделения и номер страницы
// =====================================================================

@Composable
private fun SelectionBar(
    info: SelectionInfo,
    onRecolor: () -> Unit,
    onDuplicate: () -> Unit,
    onFront: () -> Unit,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(selectionLabel(info), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 10.dp, end = 6.dp))
            if (info.strokes > 0) IconButton(onClick = onRecolor) { Icon(Icons.Outlined.FormatColorFill, "Перекрасить в текущий цвет") }
            IconButton(onClick = onDuplicate) { Icon(Icons.Outlined.ContentCopy, "Дублировать") }
            if (info.images > 0) {
                IconButton(onClick = onFront) { Icon(Icons.Outlined.FlipToFront, "На передний план") }
                IconButton(onClick = onBack) { Icon(Icons.Outlined.FlipToBack, "На задний план") }
            }
            if (info.singleImageLocked != null) IconButton(onClick = onLock) { Icon(Icons.Outlined.Lock, "Закрепить, чтобы писать поверх") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
            FilledTonalIconButton(onClick = onDone) { Icon(Icons.Filled.Check, "Готово") }
        }
    }
}

@Composable
private fun PageIndicator(
    index: Int,
    count: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenAll: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev, enabled = index > 0) { Icon(Icons.Filled.ChevronLeft, "Предыдущая страница") }
            Text(
                "${index + 1} из $count",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenAll).padding(horizontal = 8.dp, vertical = 6.dp),
            )
            IconButton(onClick = onNext, enabled = index < count - 1) { Icon(Icons.Filled.ChevronRight, "Следующая страница") }
            VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
            IconButton(onClick = onAdd) { Icon(Icons.Outlined.NoteAdd, "Новая страница") }
        }
    }
}

private fun penIcon(p: PenType): ImageVector = when (p) {
    PenType.BALLPOINT -> ToolIcons.Ballpoint
    PenType.FOUNTAIN -> ToolIcons.Fountain
    PenType.PENCIL -> ToolIcons.Pencil
    PenType.HIGHLIGHTER -> ToolIcons.Highlighter
    PenType.WATERCOLOR -> Icons.Outlined.WaterDrop
    PenType.INK -> Icons.Outlined.Create
    PenType.TEXTURED -> Icons.Outlined.Texture
}

private fun selectionLabel(s: SelectionInfo): String {
    val parts = ArrayList<String>()
    if (s.strokes > 0) parts += "Штрихи: ${s.strokes}"
    if (s.images > 0) parts += "Фото: ${s.images}"
    return parts.joinToString(" · ")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
