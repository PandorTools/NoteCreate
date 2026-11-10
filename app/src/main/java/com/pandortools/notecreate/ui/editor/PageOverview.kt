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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Страницы · ${nb.pages.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { onAddAt(nb.pages.size) }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.padding(4.dp)); Text("Страница в конец")
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(nb.pages, key = { _, id -> id }) { i, id ->
                        var menu by remember { mutableStateOf(false) }
                        val thumb by produceState<Bitmap?>(null, id, nb.paperType, nb.paperColor) { value = thumbnail(i) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(nb.aspect)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(androidx.compose.ui.graphics.Color(nb.paperColor))
                                    .border(
                                        if (i == current) 3.dp else 1.dp,
                                        if (i == current) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .combinedClickable(onClick = { onOpen(i) }, onLongClick = { menu = true }),
                            ) {
                                thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Открыть") }, onClick = { menu = false; onOpen(i) })
                                    DropdownMenuItem(text = { Text("Новая страница после") }, onClick = { menu = false; onAddAt(i + 1) })
                                    DropdownMenuItem(text = { Text("Дублировать") }, onClick = { menu = false; onDuplicate(i) })
                                    if (i > 0) DropdownMenuItem(text = { Text("Сдвинуть влево") }, onClick = { menu = false; onMove(i, i - 1) })
                                    if (i < nb.pages.size - 1) DropdownMenuItem(text = { Text("Сдвинуть вправо") }, onClick = { menu = false; onMove(i, i + 1) })
                                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete(i) })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            val date = nb.pageDates[id]
                            Text(
                                if (nb.template == NotebookTemplate.DIARY && date != null)
                                    "${i + 1} · ${PageRenderer.formatDate(date).substringAfter(", ")}"
                                else "${i + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
