@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.pandortools.notecreate.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.ui.common.AppLogo
import com.pandortools.notecreate.ui.common.NotebookCover
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Shelf(val title: String) { ALL("Все"), FAVORITES("Избранное"), TRASH("Корзина") }

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Найти тетрадь") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier,
    )
}

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
    var creating by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Notebook?>(null) }
    var confirmDelete by remember { mutableStateOf<Notebook?>(null) }

    val visible = all.filter {
        when (shelf) {
            Shelf.ALL -> it.deletedAt == null
            Shelf.FAVORITES -> it.deletedAt == null && it.favorite
            Shelf.TRASH -> it.deletedAt != null
        }
    }.filter { groupFilter == null || it.groupName.equals(groupFilter, ignoreCase = true) }
        .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
    val groups = (settings.notebookGroups + all.mapNotNull { it.groupName?.takeIf(String::isNotBlank) }).distinct().sorted()
    val groupBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingNotebook by remember { mutableStateOf<String?>(null) }
    val last = all.firstOrNull { it.id == settings.lastNotebookId && it.deletedAt == null }

    val compact = LocalConfiguration.current.screenWidthDp < 700

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!compact) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, header = {
            AppLogo(Modifier.padding(top = 12.dp).size(48.dp))
            Spacer(Modifier.height(12.dp))
        }) {
            NavigationRailItem(selected = shelf == Shelf.ALL, onClick = { shelf = Shelf.ALL },
                icon = { Icon(Icons.Outlined.Book, null) }, label = { Text(Shelf.ALL.title) })
            NavigationRailItem(selected = shelf == Shelf.FAVORITES, onClick = { shelf = Shelf.FAVORITES },
                icon = { Icon(Icons.Outlined.StarBorder, null) }, label = { Text(Shelf.FAVORITES.title) })
            NavigationRailItem(selected = shelf == Shelf.TRASH, onClick = { shelf = Shelf.TRASH },
                icon = { Icon(Icons.Outlined.Delete, null) }, label = { Text(Shelf.TRASH.title) })
            Spacer(Modifier.weight(1f))
            NavigationRailItem(selected = false, onClick = onSettings,
                icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Настройки") })
            Spacer(Modifier.height(12.dp))
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (compact) NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets.navigationBars,
                ) {
                    NavigationBarItem(selected = shelf == Shelf.ALL, onClick = { shelf = Shelf.ALL },
                        icon = { Icon(Icons.Outlined.Book, null) }, label = { Text("Тетради") })
                    NavigationBarItem(selected = shelf == Shelf.FAVORITES, onClick = { shelf = Shelf.FAVORITES },
                        icon = { Icon(Icons.Outlined.StarBorder, null) }, label = { Text("Избранное") })
                    NavigationBarItem(selected = shelf == Shelf.TRASH, onClick = { shelf = Shelf.TRASH },
                        icon = { Icon(Icons.Outlined.Delete, null) }, label = { Text("Корзина") })
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 142.dp else 170.dp),
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = if (compact) 20.dp else 36.dp, end = if (compact) 20.dp else 36.dp, top = if (compact) 18.dp else 28.dp, bottom = 108.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 22.dp else 32.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (compact) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppLogo(Modifier.size(38.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (shelf == Shelf.TRASH) "Удалённые тетради" else "NoteCreate", style = MaterialTheme.typography.titleMedium)
                                    Text("Твоё пространство для идей", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(if (shelf == Shelf.TRASH) "Корзина" else if (shelf == Shelf.FAVORITES) "Избранное" else "Мои тетради",
                                    style = MaterialTheme.typography.headlineMedium)
                                Text(if (shelf == Shelf.ALL) "Продолжай писать и сохраняй свои мысли." else "Твои тетради всегда под рукой.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (shelf == Shelf.TRASH) "Корзина" else if (shelf == Shelf.FAVORITES) "Избранное" else "Мои тетради",
                                        style = MaterialTheme.typography.headlineMedium)
                                    Text("Твоё пространство для идей", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SearchField(query, { query = it }, Modifier.widthIn(min = 220.dp, max = 320.dp))
                            }
                        }
                        if (compact) SearchField(query, { query = it }, Modifier.fillMaxWidth())
                        if (compact && shelf == Shelf.ALL && last == null) {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Тихое место для мыслей", style = MaterialTheme.typography.titleMedium)
                                        Text("Создай тетрадь и начни с чистого листа", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        if (compact && shelf == Shelf.ALL) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = true, onClick = { shelf = Shelf.ALL }, label = { Text("Все") },
                                    leadingIcon = { Icon(Icons.Outlined.Book, null, Modifier.size(18.dp)) })
                                FilterChip(selected = false, onClick = { shelf = Shelf.FAVORITES }, label = { Text("Избранное") },
                                    leadingIcon = { Icon(Icons.Outlined.StarBorder, null, Modifier.size(18.dp)) })
                            }
                        }
                        if (shelf == Shelf.ALL) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = groupFilter == null, onClick = { groupFilter = null }, label = { Text("Все группы") })
                                groups.forEach { group ->
                                    FilterChip(selected = groupFilter.equals(group, ignoreCase = true),
                                        onClick = { groupFilter = group }, label = { Text(group) },
                                        modifier = Modifier.onGloballyPositioned { groupBounds[group] = it.boundsInRoot() })
                                }
                                FilterChip(selected = false, onClick = { newGroupName = ""; creatingGroup = true },
                                    label = { Text("+ Категория") })
                            }
                            if (groups.isNotEmpty()) Text(
                                if (draggingNotebook != null) "Отпусти тетрадь на категории, чтобы переместить" else "Чтобы разложить тетради, удерживай обложку и перетащи её на категорию",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (shelf == Shelf.ALL && last != null && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { ContinueCard(last) { onOpen(last) } }
                }
                if (shelf == Shelf.TRASH && visible.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Тетради удаляются навсегда через 30 дней.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            when (shelf) {
                                Shelf.ALL -> "Пока пусто. Создай первую тетрадь — кнопка справа внизу."
                                Shelf.FAVORITES -> "Отметь тетради звёздочкой, и они появятся здесь."
                                Shelf.TRASH -> "Корзина пуста."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 40.dp),
                        )
                    }
                }
                items(visible, key = { it.id }) { nb ->
                    NotebookTile(
                        nb = nb,
                        inTrash = shelf == Shelf.TRASH,
                        onOpen = { onOpen(nb) },
                        onEdit = { editing = nb },
                        onFavorite = { scope.launch { repo.save(nb.copy(favorite = !nb.favorite)) } },
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

    if (creatingGroup) {
        AlertDialog(
            onDismissRequest = { creatingGroup = false },
            title = { Text("Новая категория") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Например: Учёба, Работа или Личные записи")
                    OutlinedTextField(value = newGroupName, onValueChange = { newGroupName = it },
                        label = { Text("Название категории") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = newGroupName.isNotBlank(), onClick = {
                    val name = newGroupName.trim()
                    if (name.isNotEmpty() && groups.none { it.equals(name, ignoreCase = true) }) {
                        AppGraph.settings.update { it.copy(notebookGroups = (it.notebookGroups + name).distinct()) }
                    }
                    groupFilter = null
                    creatingGroup = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { creatingGroup = false }) { Text("Отмена") } },
        )
    }

    if (creating) {
        NotebookSetupDialog(existing = null, defaults = settings, onDismiss = { creating = false }, groups = groups) { spec ->
            creating = false
            scope.launch {
                val nb = repo.create(spec)
                onOpen(nb)
            }
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
            title = { Text("Удалить навсегда?") },
            text = { Text("«${nb.title}» и все её страницы будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; scope.launch { repo.deleteForever(nb) } }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ContinueCard(nb: Notebook, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NotebookCover(nb.title, nb.coverColor, nb.coverMaterial, nb.elastic, nb.ribbon,
                Modifier.width(56.dp).aspectRatio(nb.aspect), seed = nb.id.hashCode())
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Продолжить", style = MaterialTheme.typography.titleLarge)
                Text("${nb.title} · страница ${nb.lastPage + 1} из ${nb.pages.size}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            }
            Icon(Icons.Filled.Edit, null)
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
    val density = LocalDensity.current.density
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("ru")) }
    var tileCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastDragPoint by remember { mutableStateOf(Offset.Zero) }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(nb.aspect)
                .graphicsLayer { scaleX = lift.value; scaleY = lift.value }
                .onGloballyPositioned { tileCoordinates = it }
                .pointerInput(nb.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            onDragStarted(nb)
                            tileCoordinates?.localToRoot(local)?.let { lastDragPoint = it }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            tileCoordinates?.localToRoot(change.position)?.let { lastDragPoint = it }
                        },
                        onDragEnd = { onDragEnded(nb, lastDragPoint) },
                        onDragCancel = { onDragEnded(nb, Offset.Unspecified) },
                    )
                }
                .combinedClickable(
                    onClick = {
                        if (inTrash) { menu = true; return@combinedClickable }
                        scope.launch {
                            // анимация: тетрадь приподнимается и обложка открывается
                            launch { lift.animateTo(1.06f, tween(180)) }
                            rotation.animateTo(-105f, tween(420, easing = FastOutSlowInEasing))
                            onOpen()
                            rotation.snapTo(0f); lift.snapTo(1f)
                        }
                    },
                    onLongClick = { menu = true },
                ),
        ) {
            // первая страница под обложкой
            Box(
                Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(nb.paperColor), RoundedCornerShape(6.dp)),
            )
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
                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp))
            }
            IconButton(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopStart)
                .padding(4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), CircleShape)) {
                Icon(Icons.Filled.MoreVert, "Действия с тетрадью")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (inTrash) {
                    DropdownMenuItem(text = { Text("Восстановить") }, leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                        onClick = { menu = false; onRestore() })
                    DropdownMenuItem(text = { Text("Удалить навсегда") }, leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) },
                        onClick = { menu = false; onDeleteForever() })
                } else {
                    DropdownMenuItem(text = { Text("Открыть") }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Обложка и настройки") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text(if (nb.favorite) "Убрать из избранного" else "В избранное") },
                        leadingIcon = { Icon(if (nb.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                        onClick = { menu = false; onFavorite() })
                    DropdownMenuItem(text = { Text("В корзину") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(nb.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${nb.pages.size} стр. · ${dateFmt.format(Date(nb.updatedAt))}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
