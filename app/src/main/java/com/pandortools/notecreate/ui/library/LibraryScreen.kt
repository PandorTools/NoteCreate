@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.pandortools.notecreate.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.ui.common.AppLogo
import com.pandortools.notecreate.ui.common.EmptyState
import com.pandortools.notecreate.ui.common.NotebookCover
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

private enum class Shelf(val title: String) { ALL("Все тетради"), FAVORITES("Избранное"), TRASH("Корзина") }

@Composable
fun LibraryScreen(
    onOpen: (Notebook) -> Unit,
    onSettings: () -> Unit,
) {
    val repo = AppGraph.repository
    val settings by AppGraph.settings.state.collectAsState()
    val all by repo.notebooks.collectAsState()
    val scope = rememberCoroutineScope()
    var shelf by rememberSaveable { mutableStateOf(Shelf.ALL) }
    var groupFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var handwritingResults by remember { mutableStateOf<List<com.pandortools.notecreate.data.NotebookRepository.HandwritingMatch>>(emptyList()) }
    var searchingHandwriting by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var confirmDelete by remember { mutableStateOf<Notebook?>(null) }

    val groups = (settings.notebookGroups + all.mapNotNull { it.groupName?.takeIf(String::isNotBlank) }).distinct().sorted()
    val visible = all.filter {
        when (shelf) {
            Shelf.ALL -> it.deletedAt == null
            Shelf.FAVORITES -> it.deletedAt == null && it.favorite
            Shelf.TRASH -> it.deletedAt != null
        }
    }.filter { shelf != Shelf.ALL || groupFilter == null || it.groupName.equals(groupFilter, ignoreCase = true) }
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val groupBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingNotebook by remember { mutableStateOf<String?>(null) }
    val last = all.firstOrNull { it.id == settings.lastNotebookId && it.deletedAt == null }
    val today = Calendar.getInstance()
    val monthAgo = (today.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val yearAgo = (today.clone() as Calendar).apply { add(Calendar.YEAR, -1) }
    val memories = all.filter { it.deletedAt == null }.flatMap { nb ->
        nb.pages.mapIndexedNotNull { index, pageId ->
            val millis = nb.pageDates[pageId] ?: return@mapIndexedNotNull null
            val date = Calendar.getInstance().apply { timeInMillis = millis }
            val matchesMonth = date.get(Calendar.YEAR) == monthAgo.get(Calendar.YEAR) &&
                date.get(Calendar.MONTH) == monthAgo.get(Calendar.MONTH) && date.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            val matchesYear = date.get(Calendar.YEAR) == yearAgo.get(Calendar.YEAR) &&
                date.get(Calendar.MONTH) == today.get(Calendar.MONTH) && date.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
            if (matchesMonth || matchesYear) MemoryEntry(nb, index, millis, if (matchesMonth) "Месяц назад" else "Год назад") else null
        }
    }
    val compact = LocalConfiguration.current.screenWidthDp < 700

    LaunchedEffect(query, shelf) {
        if (query.trim().length < 2 || shelf != Shelf.ALL) {
            handwritingResults = emptyList()
            searchingHandwriting = false
        } else {
            searchingHandwriting = true
            delay(800)
            try { handwritingResults = repo.searchHandwriting(query) }
            finally { searchingHandwriting = false }
        }
    }

    fun selectShelf(s: Shelf) { shelf = s; if (s != Shelf.ALL) groupFilter = null }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!compact) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                header = { AppLogo(Modifier.padding(vertical = 16.dp).size(44.dp)) },
            ) {
                Shelf.entries.forEach { s ->
                    NavigationRailItem(
                        selected = shelf == s, onClick = { selectShelf(s) },
                        icon = { Icon(shelfIcon(s, shelf == s), null) },
                        label = { Text(shortTitle(s)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                NavigationRailItem(selected = false, onClick = onSettings,
                    icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Настройки") })
                Spacer(Modifier.height(16.dp))
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (compact) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Shelf.entries.forEach { s ->
                        NavigationBarItem(selected = shelf == s, onClick = { selectShelf(s) },
                            icon = { Icon(shelfIcon(s, shelf == s), null) }, label = { Text(shortTitle(s)) })
                    }
                    NavigationBarItem(selected = false, onClick = onSettings,
                        icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Настройки") })
                }
            },
            floatingActionButton = {
                if (shelf != Shelf.TRASH) {
                    ExtendedFloatingActionButton(
                        onClick = { creating = true },
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("Новая тетрадь") },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    )
                }
            },
        ) { pad ->
            val side = if (compact) 16.dp else 32.dp
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 140.dp else 168.dp),
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = side, end = side, top = if (compact) 12.dp else 24.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp),
            ) {
                // ---------- Заголовок и поиск ----------
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (compact) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppLogo(Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(shelf.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            }
                            SearchField(query, { query = it }, Modifier.fillMaxWidth())
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(shelf.title, style = MaterialTheme.typography.headlineMedium)
                                    Text(countLabel(visible.size), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SearchField(query, { query = it }, Modifier.widthIn(min = 240.dp, max = 340.dp))
                            }
                        }

                        if (shelf == Shelf.ALL) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GroupChip("Все", Icons.AutoMirrored.Outlined.MenuBook, groupFilter == null, false) { groupFilter = null }
                                groups.forEach { group ->
                                    GroupChip(
                                        group, Icons.Outlined.Folder,
                                        selected = groupFilter.equals(group, ignoreCase = true),
                                        highlight = draggingNotebook != null,
                                        modifier = Modifier.onGloballyPositioned { groupBounds[group] = it.boundsInRoot() },
                                    ) { groupFilter = group }
                                }
                                GroupChip("Категория", Icons.Outlined.CreateNewFolder, false, false) {
                                    newGroupName = ""; creatingGroup = true
                                }
                            }
                            if (draggingNotebook != null && groups.isNotEmpty()) {
                                Text("Отпусти тетрадь на нужной категории", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (shelf == Shelf.TRASH && visible.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Тетради из корзины удаляются навсегда через 30 дней",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // ---------- Продолжить ----------
                if (shelf == Shelf.ALL && groupFilter == null && last != null && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(last) { onOpen(last) } }
                }

                if (shelf == Shelf.ALL && groupFilter == null && query.isBlank() && memories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val memory = memories.first()
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    val target = memory.notebook.copy(lastPage = memory.pageIndex)
                                    repo.save(target)
                                    onOpen(target)
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("В этот день · ${memory.relation}", style = MaterialTheme.typography.labelLarge)
                                    Text("${memory.notebook.title} · стр. ${memory.pageIndex + 1} · ${dateLabel(memory.timestamp)}",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                if (shelf == Shelf.ALL && groupFilter == null && query.trim().length >= 2) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                when {
                                    searchingHandwriting -> "Ищу в рукописных заметках…"
                                    handwritingResults.isEmpty() -> "В рукописных заметках совпадений нет"
                                    else -> "Найдено в рукописи: ${handwritingResults.size}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (handwritingResults.isEmpty() && query.trim().length >= 2) {
                                Text("При первом поиске загрузится русский языковой пакет распознавания (около 20 МБ). Дальше поиск работает на устройстве.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            handwritingResults.forEach { match ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        scope.launch {
                                            val target = match.notebook.copy(lastPage = match.pageIndex)
                                            repo.save(target)
                                            onOpen(target)
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("${match.notebook.title} · стр. ${match.pageIndex + 1}", style = MaterialTheme.typography.titleSmall)
                                        Text(match.recognizedText, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- Пусто ----------
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        when {
                            query.isNotBlank() -> EmptyState(Icons.Outlined.Search, "Ничего не нашлось",
                                "Нет тетрадей с названием «${query.trim()}»")
                            shelf == Shelf.FAVORITES -> EmptyState(Icons.Outlined.StarBorder, "Нет избранных",
                                "Открой меню тетради и отметь её звёздочкой")
                            shelf == Shelf.TRASH -> EmptyState(Icons.Outlined.Delete, "Корзина пуста",
                                "Сюда попадают удалённые тетради")
                            groupFilter != null -> EmptyState(Icons.Outlined.FolderOpen, "В категории пусто",
                                "Удерживай обложку тетради и перетащи её на категорию")
                            else -> EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "Здесь будут твои тетради",
                                "Создай первую — выбери обложку, формат и бумагу") {
                                FilledTonalButton(onClick = { creating = true }) {
                                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Создать тетрадь")
                                }
                            }
                        }
                    }
                }

                // ---------- Тетради ----------
                items(visible, key = { it.id }) { nb ->
                    NotebookTile(
                        nb = nb,
                        inTrash = shelf == Shelf.TRASH,
                        onOpen = { onOpen(nb) },
                        onEdit = { editing = nb },
                        onFavorite = { scope.launch { repo.save(nb.copy(favorite = !nb.favorite)) } },
                        onRemoveFromGroup = { scope.launch { repo.save(nb.copy(groupName = null)) } },
                        onDelete = { scope.launch { repo.moveToTrash(nb) } },
                        onRestore = { scope.launch { repo.restore(nb) } },
                        onDeleteForever = { confirmDelete = nb },
                        onDragStarted = { draggingNotebook = it.id },
                        onDragEnded = { dragged, point ->
                            val target = groups.firstOrNull { groupBounds[it]?.contains(point) == true }
                            if (target != null) scope.launch { repo.save(dragged.copy(groupName = target)) }
                            draggingNotebook = null
                        },
                    )
                }
            }
        }
    }

    // ---------- Диалоги ----------
    if (creatingGroup) {
        AlertDialog(
            onDismissRequest = { creatingGroup = false },
            icon = { Icon(Icons.Outlined.CreateNewFolder, null) },
            title = { Text("Новая категория") },
            text = {
                OutlinedTextField(
                    value = newGroupName, onValueChange = { newGroupName = it },
                    placeholder = { Text("Учёба, работа, личное…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = newGroupName.isNotBlank(), onClick = {
                    val name = newGroupName.trim()
                    if (name.isNotEmpty() && groups.none { it.equals(name, ignoreCase = true) }) {
                        AppGraph.settings.update { it.copy(notebookGroups = (it.notebookGroups + name).distinct()) }
                    }
                    creatingGroup = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text("Отмена") } },
        )
    }

    if (creating) {
        NotebookSetupDialog(existing = null, defaults = settings, onDismiss = { creating = false },
            groups = groups, initialGroup = groupFilter) { spec ->
            creating = false
            scope.launch { onOpen(repo.create(spec)) }
        }
    }
    editing?.let { nb ->
        NotebookSetupDialog(existing = nb, defaults = settings, onDismiss = { editing = null }, groups = groups) { spec ->
            editing = null
            scope.launch { repo.applySpec(nb, spec) }
        }
    }
    confirmDelete?.let { nb ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text("Удалить навсегда?") },
            text = { Text("«${nb.title}» и все её страницы исчезнут без возможности восстановить.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; scope.launch { repo.deleteForever(nb) } }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

private data class MemoryEntry(val notebook: Notebook, val pageIndex: Int, val timestamp: Long, val relation: String)
private fun dateLabel(timestamp: Long) = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))

private fun shelfIcon(s: Shelf, selected: Boolean): ImageVector = when (s) {
    Shelf.ALL -> Icons.AutoMirrored.Outlined.MenuBook
    Shelf.FAVORITES -> if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder
    Shelf.TRASH -> Icons.Outlined.Delete
}

private fun shortTitle(s: Shelf) = when (s) {
    Shelf.ALL -> "Тетради"
    Shelf.FAVORITES -> "Избранное"
    Shelf.TRASH -> "Корзина"
}

/** «1 тетрадь», «3 тетради», «7 тетрадей». */
private fun countLabel(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> "тетрадь"
        m10 in 2..4 && m100 !in 12..14 -> "тетради"
        else -> "тетрадей"
    }
    return "$n $word"
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Поиск по названию") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, "Очистить") }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

@Composable
private fun GroupChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected || highlight,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    )
}

@Composable
private fun ContinueCard(nb: Notebook, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NotebookCover(nb.title, nb.coverColor, nb.coverMaterial, nb.elastic, nb.ribbon,
                Modifier.width(52.dp).aspectRatio(nb.aspect), seed = nb.id.hashCode(), showTitle = false)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Продолжить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(nb.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Страница ${nb.lastPage + 1} из ${nb.pages.size}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Открыть", tint = MaterialTheme.colorScheme.onSecondary)
                }
            }
        }
    }
}

@Composable
private fun NotebookTile(
    nb: Notebook,
    inTrash: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onRemoveFromGroup: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onDragStarted: (Notebook) -> Unit,
    onDragEnded: (Notebook, Offset) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val lift = remember { Animatable(1f) }
    var menu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val dateFmt = remember { SimpleDateFormat("d MMM", Locale("ru")) }
    var tileCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastDragPoint by remember { mutableStateOf(Offset.Zero) }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(nb.aspect)
                .graphicsLayer {
                    val s = if (dragging) 0.92f else lift.value
                    scaleX = s; scaleY = s
                    alpha = if (dragging) 0.85f else 1f
                }
                .onGloballyPositioned { tileCoordinates = it }
                .pointerInput(nb.id, inTrash) {
                    if (inTrash) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            dragging = true
                            onDragStarted(nb)
                            tileCoordinates?.localToRoot(local)?.let { lastDragPoint = it }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            tileCoordinates?.localToRoot(change.position)?.let { lastDragPoint = it }
                        },
                        onDragEnd = { dragging = false; onDragEnded(nb, lastDragPoint) },
                        onDragCancel = { dragging = false; onDragEnded(nb, Offset.Unspecified) },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (inTrash) { menu = true; return@combinedClickable }
                        scope.launch {
                            // тетрадь приподнимается, обложка открывается
                            launch { lift.animateTo(1.05f, tween(180)) }
                            rotation.animateTo(-105f, tween(420, easing = FastOutSlowInEasing))
                            onOpen()
                            rotation.snapTo(0f); lift.snapTo(1f)
                        }
                    },
                    onLongClick = { if (inTrash) menu = true },
                ),
        ) {
            // первая страница под обложкой
            Box(Modifier.fillMaxSize().background(Color(nb.paperColor), RoundedCornerShape(6.dp)))
            NotebookCover(
                nb.title, nb.coverColor, nb.coverMaterial, nb.elastic, nb.ribbon,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotation.value
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 14f * density
                    },
                seed = nb.id.hashCode(),
            )
            if (nb.favorite) {
                Surface(
                    shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Filled.Star, "В избранном", tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(4.dp).size(16.dp))
                }
            }
        }

        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(nb.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(nb.groupName, "${nb.pages.size} стр.", dateFmt.format(Date(nb.updatedAt))).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, "Действия с тетрадью", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (inTrash) {
                        DropdownMenuItem(text = { Text("Восстановить") }, leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                            onClick = { menu = false; onRestore() })
                        DropdownMenuItem(
                            text = { Text("Удалить навсегда", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDeleteForever() })
                    } else {
                        DropdownMenuItem(text = { Text("Открыть") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                            onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Обложка и настройки") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(if (nb.favorite) "Убрать из избранного" else "В избранное") },
                            leadingIcon = { Icon(if (nb.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                            onClick = { menu = false; onFavorite() })
                        if (nb.groupName != null) {
                            DropdownMenuItem(text = { Text("Убрать из категории") },
                                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                onClick = { menu = false; onRemoveFromGroup() })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("В корзину", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
