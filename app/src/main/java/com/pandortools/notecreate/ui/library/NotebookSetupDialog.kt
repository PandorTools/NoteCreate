@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pandortools.notecreate.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pandortools.notecreate.data.CoverMaterial
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.data.NotebookSpec
import com.pandortools.notecreate.data.NotebookTemplate
import com.pandortools.notecreate.data.Orientation
import com.pandortools.notecreate.data.PageFormat
import com.pandortools.notecreate.data.Palette
import com.pandortools.notecreate.data.PaperType
import com.pandortools.notecreate.data.SettingsState
import com.pandortools.notecreate.ink.PageRenderer
import com.pandortools.notecreate.ui.common.NotebookCover

/** Создание новой тетради или изменение существующей. */
@Composable
fun NotebookSetupDialog(
    existing: Notebook?,
    defaults: SettingsState,
    onDismiss: () -> Unit,
    groups: List<String> = emptyList(),
    onConfirm: (NotebookSpec) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var template by remember { mutableStateOf(existing?.template ?: NotebookTemplate.DIARY) }
    var coverColor by remember { mutableIntStateOf(existing?.coverColor ?: Palette.coverColors.random()) }
    var material by remember { mutableStateOf(existing?.coverMaterial ?: CoverMaterial.LEATHER) }
    var elastic by remember { mutableStateOf(existing?.elastic ?: true) }
    var ribbon by remember { mutableStateOf(existing?.ribbon ?: true) }
    var format by remember { mutableStateOf(existing?.format ?: defaults.defaultFormat) }
    var customWidth by remember { mutableFloatStateOf(existing?.customWidthMm ?: 210f) }
    var customHeight by remember { mutableFloatStateOf(existing?.customHeightMm ?: 297f) }
    var groupName by remember { mutableStateOf(existing?.groupName.orEmpty()) }
    var orientation by remember { mutableStateOf(existing?.orientation ?: defaults.defaultOrientation) }
    var paper by remember { mutableStateOf(existing?.paperType ?: defaults.defaultPaper) }
    var paperColor by remember { mutableIntStateOf(existing?.paperColor ?: defaults.defaultPaperColor) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 920.dp)
                .heightIn(max = 760.dp),
        ) {
            BoxWithConstraints {
                val wide = maxWidth > 640.dp
                val rawW = if (format == PageFormat.CUSTOM) customWidth else format.widthMm
                val rawH = if (format == PageFormat.CUSTOM) customHeight else format.heightMm
                val aspect = if (orientation == Orientation.PORTRAIT) rawW / rawH else rawH / rawW
                val preview: @Composable (Modifier) -> Unit = { m ->
                    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                        NotebookCover(
                            title = title.ifBlank { "Мой дневник" },
                            color = coverColor, material = material, elastic = elastic, ribbon = ribbon,
                            modifier = Modifier.width(if (wide) 220.dp else 150.dp).aspectRatio(aspect),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Canvas(Modifier.size(36.dp, 48.dp).clip(RoundedCornerShape(5.dp))) { drawPaperIcon(paper, paperColor) }
                            Column {
                                Text("${format.title} · ${rawW.toInt()} × ${rawH.toInt()} мм",
                                    style = MaterialTheme.typography.labelLarge)
                                Text("${orientation.title} · ${paper.title.lowercase()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                val options: @Composable (Modifier) -> Unit = { m ->
                    Column(m.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (existing == null) "Новая тетрадь" else "Настройки тетради", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = groupName, onValueChange = { groupName = it },
                            label = { Text("Группа (необязательно)") },
                            supportingText = { Text("Например: Учёба, Работа или Идеи") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        if (groups.isNotEmpty()) Section("Или выбрать группу") {
                            groups.forEach { group ->
                                FilterChip(selected = groupName.equals(group, ignoreCase = true),
                                    onClick = { groupName = group }, label = { Text(group) })
                            }
                        }
                        Section("Тип") {
                            NotebookTemplate.entries.forEach { t ->
                                FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.title) })
                            }
                        }
                        Section("Цвет обложки") {
                            Palette.coverColors.forEach { c -> ColorDot(c, coverColor == c) { coverColor = c } }
                        }
                        Section("Материал") {
                            CoverMaterial.entries.forEach { m2 ->
                                FilterChip(selected = material == m2, onClick = { material = m2 }, label = { Text(m2.title) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Резинка", Modifier.weight(1f))
                            Switch(checked = elastic, onCheckedChange = { elastic = it })
                            Spacer(Modifier.width(24.dp))
                            Text("Ленточка", Modifier.weight(1f))
                            Switch(checked = ribbon, onCheckedChange = { ribbon = it })
                        }
                        Section("Формат листа") {
                            PageFormat.entries.forEach { f ->
                                FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.title) })
                            }
                        }
                        if (format == PageFormat.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(value = customWidth.toInt().toString(), onValueChange = { customWidth = it.toFloatOrNull()?.coerceIn(50f, 1000f) ?: customWidth },
                                    label = { Text("Ширина, мм") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = customHeight.toInt().toString(), onValueChange = { customHeight = it.toFloatOrNull()?.coerceIn(50f, 1000f) ?: customHeight },
                                    label = { Text("Высота, мм") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                        }
                        Section("Ориентация") {
                            Orientation.entries.forEach { o ->
                                FilterChip(selected = orientation == o, onClick = { orientation = o }, label = { Text(o.title) })
                            }
                        }
                        Section("Бумага") {
                            PaperType.entries.forEach { p ->
                                PaperOption(p, paperColor, paper == p) { paper = p }
                            }
                        }
                        Section("Цвет бумаги") {
                            Palette.paperColors.forEach { c -> ColorDot(c, paperColor == c) { paperColor = c } }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss) { Text("Отмена") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                onConfirm(
                                    NotebookSpec(
                                        title = title.ifBlank { if (template == NotebookTemplate.DIARY) "Мой дневник" else "Тетрадь" },
                                        template = template, coverColor = coverColor, coverMaterial = material,
                                        elastic = elastic, ribbon = ribbon, paperType = paper, paperColor = paperColor,
                                        format = format, orientation = orientation,
                                        customWidthMm = if (format == PageFormat.CUSTOM) customWidth else null,
                                        customHeightMm = if (format == PageFormat.CUSTOM) customHeight else null,
                                        groupName = groupName.trim().takeIf { it.isNotEmpty() },
                                    )
                                )
                            }) { Text(if (existing == null) "Создать" else "Сохранить") }
                        }
                    }
                }
                if (wide) {
                    Row(Modifier.padding(24.dp)) {
                        Box(Modifier.width(260.dp).padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                            preview(Modifier)
                        }
                        Spacer(Modifier.width(24.dp))
                        options(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.padding(20.dp)) {
                        preview(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        options(Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
fun ColorDot(color: Int, selected: Boolean, size: Int = 36, onClick: () -> Unit) {
    val c = Color(color)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(c)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.secondary else Color.Black.copy(alpha = 0.15f)),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = if (PageRenderer.isDark(color)) Color.White else Color.Black, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun PaperOption(type: PaperType, paperColor: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(40.dp, 52.dp).clip(RoundedCornerShape(3.dp))) { drawPaperIcon(type, paperColor) }
        Spacer(Modifier.height(4.dp))
        Text(type.title, style = MaterialTheme.typography.labelSmall)
    }
}

fun DrawScope.drawPaperIcon(type: PaperType, paperColor: Int) {
    drawRect(Color(paperColor))
    val dark = PageRenderer.isDark(paperColor)
    val line = if (dark) Color.White.copy(alpha = 0.35f) else Color(0xFF4677A6).copy(alpha = 0.45f)
    val w = size.width
    val h = size.height
    when (type) {
        PaperType.BLANK -> Unit
        PaperType.LINED, PaperType.LINED_WIDE -> {
            val step = if (type == PaperType.LINED) h / 9f else h / 7f
            var y = step * 1.5f
            while (y < h - 2f) { drawLine(line, Offset(0f, y), Offset(w, y), 1.5f); y += step }
            drawLine(Color(0xFFD9534F).copy(alpha = 0.5f), Offset(w * 0.18f, 0f), Offset(w * 0.18f, h), 1.5f)
        }
        PaperType.GRID -> {
            val step = w / 6f
            var x = step
            while (x < w) { drawLine(line, Offset(x, 0f), Offset(x, h), 1f); x += step }
            var y = step
            while (y < h) { drawLine(line, Offset(0f, y), Offset(w, y), 1f); y += step }
        }
        PaperType.DOTS -> {
            val step = w / 6f
            var y = step
            while (y < h) {
                var x = step
                while (x < w) { drawCircle(line, 1.6f, Offset(x, y)); x += step }
                y += step
            }
        }
        PaperType.MUSIC -> {
            var top = h * 0.15f
            while (top + h * 0.2f < h) {
                for (i in 0..4) drawLine(line, Offset(w * 0.08f, top + i * h * 0.04f), Offset(w * 0.92f, top + i * h * 0.04f), 1f)
                top += h * 0.32f
            }
        }
    }
}
