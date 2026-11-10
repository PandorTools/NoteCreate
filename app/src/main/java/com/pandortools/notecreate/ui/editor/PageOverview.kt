@file:OptIn(ExperimentalFoundationApi::class)

package com.pandortools.notecreate.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.data.NotebookTemplate
import com.pandortools.notecreate.ink.PageRenderer

/** Все страницы миниатюрами: переход, добавление, дублирование, перемещение, удаление. */
@Composable
fun PageOverviewDialog(
    nb: Notebook,
    current: Int,
    thumbnail: suspend (Int) -> Bitmap?,
    onDismiss: () -> Unit,
    onOpen: (Int) -> Unit,
    onAddAt: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Страницы", style = MaterialTheme.typography.titleLarge)
                        Text("${nb.title} · ${nb.pages.size} стр.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { onAddAt(nb.pages.size) }) {
                        Icon(Icons.Outlined.NoteAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("В конец")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    itemsIndexed(nb.pages, key = { _, id -> id }) { i, id ->
                        var menu by remember { mutableStateOf(false) }
                        val thumb by produceState<Bitmap?>(null, id, nb.paperType, nb.paperColor) { value = thumbnail(i) }
                        val isCurrent = i == current
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(nb.aspect)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(androidx.compose.ui.graphics.Color(nb.paperColor))
                                    .border(
                                        if (isCurrent) 3.dp else 1.dp,
                                        if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .combinedClickable(onClick = { onOpen(i) }, onLongClick = { menu = true }),
                            ) {
                                thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(
                                        if (isCurrent) "${i + 1} · открыта" else "${i + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isCurrent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    val date = nb.pageDates[id]
                                    if (nb.template == NotebookTemplate.DIARY && date != null) {
                                        Text(PageRenderer.formatDate(date).substringAfter(", "),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Box {
                                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Filled.MoreVert, "Действия со страницей", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Открыть") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                                            onClick = { menu = false; onOpen(i) })
                                        DropdownMenuItem(text = { Text("Новая страница после") },
                                            leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) },
                                            onClick = { menu = false; onAddAt(i + 1) })
                                        DropdownMenuItem(text = { Text("Дублировать") },
                                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                            onClick = { menu = false; onDuplicate(i) })
                                        if (i > 0) DropdownMenuItem(text = { Text("Сдвинуть раньше") },
                                            leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                                            onClick = { menu = false; onMove(i, i - 1) })
                                        if (i < nb.pages.size - 1) DropdownMenuItem(text = { Text("Сдвинуть позже") },
                                            leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                                            onClick = { menu = false; onMove(i, i + 1) })
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                            colors = MenuDefaults.itemColors(),
                                            onClick = { menu = false; onDelete(i) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
