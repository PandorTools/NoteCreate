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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import com.pandortools.notecreate.ui.common.IconChip
import com.pandortools.notecreate.ui.common.SegmentedChoice
import com.pandortools.notecreate.ui.common.SettingIcon
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
    initialGroup: String? = null,
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
    var groupName by remember { mutableStateOf(existing?.groupName ?: initialGroup.orEmpty()) }
    var orientation by remember { mutableStateOf(existing?.orientation ?: defaults.defaultOrientation) }
    var paper by remember { mutableStateOf(existing?.paperType ?: defaults.defaultPaper) }
    var paperColor by remember { mutableIntStateOf(existing?.paperColor ?: defaults.defaultPaperColor) }

    val rawW = if (format == PageFormat.CUSTOM) customWidth else format.widthMm
    val rawH = if (format == PageFormat.CUSTOM) customHeight else format.heightMm
    val pageW = if (orientation == Orientation.PORTRAIT) rawW else rawH
    val pageH = if (orientation == Orientation.PORTRAIT) rawH else rawW
    val aspect = pageW / pageH
    val defaultTitle = if (template == NotebookTemplate.DIARY) "Мой дневник" else "Тетрадь"

    fun confirm() = onConfirm(
        NotebookSpec(
            title = title.ifBlank { defaultTitle },
            template = template, coverColor = coverColor, coverMaterial = material,
            elastic = elastic, ribbon = ribbon, paperType = paper, paperColor = paperColor,
            format = format, orientation = orientation,
            customWidthMm = if (format == PageFormat.CUSTOM) customWidth else null,
            customHeightMm = if (format == PageFormat.CUSTOM) customHeight else null,
            groupName = groupName.trim().takeIf { it.isNotEmpty() },
        )
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 980.dp).heightIn(max = 820.dp),
        ) {
            Column {
                // ---------- Заголовок ----------
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SettingIcon(if (existing == null) Icons.Outlined.NoteAdd else Icons.Outlined.Edit)
                    Spacer(Modifier.width(14.dp))
                    Text(if (existing == null) "Новая тетрадь" else "Настройки тетради",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val preview: @Composable (Modifier, Dp) -> Unit = { m, coverWidth ->
                    Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        NotebookCover(
                            title = title.ifBlank { defaultTitle },
                            color = coverColor, material = material, elastic = elastic, ribbon = ribbon,
                            modifier = Modifier.width(coverWidth).aspectRatio(aspect),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(30.dp, 40.dp).clip(RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))) {
                                drawPaperIcon(paper, paperColor)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${pageW.toInt()} × ${pageH.toInt()} мм", style = MaterialTheme.typography.labelLarge)
                                Text("${format.title} · ${paper.title.lowercase()}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                val options: @Composable (Modifier) -> Unit = { m ->
                    Column(m.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        // Основное
                        FormSection(Icons.Outlined.Edit, "Основное") {
                            OutlinedTextField(
                                value = title, onValueChange = { title = it },
                                label = { Text("Название") }, placeholder = { Text(defaultTitle) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            SegmentedChoice(
                                options = listOf(NotebookTemplate.DIARY, NotebookTemplate.NOTEBOOK),
                                selected = template, label = { it.title },
                                optionIcon = { if (it == NotebookTemplate.DIARY) Icons.Outlined.Today else Icons.AutoMirrored.Outlined.MenuBook },
                                onSelect = { template = it },
                            )
                            Text(
                                if (template == NotebookTemplate.DIARY) "У каждой страницы своя дата; новый день начинается с нового листа."
                                else "Обычная тетрадь без дат: конспекты, заметки, рисунки.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = groupName, onValueChange = { groupName = it },
                                label = { Text("Категория") }, placeholder = { Text("Без категории") },
                                leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            if (groups.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    groups.forEach { g ->
                                        IconChip(groupName.equals(g, ignoreCase = true), g, Icons.Outlined.Folder) {
                                            groupName = if (groupName.equals(g, ignoreCase = true)) "" else g
                                        }
                                    }
                                }
                            }
                        }

                        // Обложка
                        FormSection(Icons.Outlined.ColorLens, "Обложка") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Palette.coverColors.forEach { c -> ColorDot(c, coverColor == c) { coverColor = c } }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CoverMaterial.entries.forEach { m2 -> IconChip(material == m2, m2.title, Icons.Outlined.Texture) { material = m2 } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ToggleTile(Icons.Outlined.LinearScale, "Резинка", elastic, Modifier.weight(1f)) { elastic = !elastic }
                                ToggleTile(Icons.Outlined.BookmarkBorder, "Ленточка", ribbon, Modifier.weight(1f)) { ribbon = !ribbon }
                            }
                        }

                        // Лист
                        FormSection(Icons.Outlined.AspectRatio, "Лист") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageFormat.entries.forEach { f -> IconChip(format == f, f.title) { format = f } }
                            }
                            if (format == PageFormat.CUSTOM) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MmField("Ширина", customWidth, Modifier.weight(1f)) { customWidth = it }
                                    MmField("Высота", customHeight, Modifier.weight(1f)) { customHeight = it }
                                }
                            }
                            SegmentedChoice(
                                options = Orientation.entries, selected = orientation, label = { it.title },
                                optionIcon = { if (it == Orientation.PORTRAIT) Icons.Outlined.CropPortrait else Icons.Outlined.CropLandscape },
                                onSelect = { orientation = it },
                            )
                        }

                        // Бумага
                        FormSection(Icons.Outlined.GridOn, "Бумага") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PaperType.entries.forEach { p -> PaperOption(p, paperColor, paper == p) { paper = p } }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Palette.paperColors.forEach { c -> ColorDot(c, paperColor == c) { paperColor = c } }
                            }
                        }
                    }
                }

                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    if (maxWidth > 680.dp) {
                        Row {
                            Box(
                                Modifier.width(300.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) { preview(Modifier, 200.dp) }
                            options(Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
                                contentAlignment = Alignment.Center) { preview(Modifier, 120.dp) }
                            options(Modifier.heightIn(max = 10_000.dp))
                        }
                    }
                }

                // ---------- Кнопки ----------
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { confirm() }) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (existing == null) "Создать" else "Сохранить")
                    }
                }
            }
        }
    }
}

/** Блок формы: заголовок с иконкой и содержимое. */
@Composable
fun FormSection(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun ToggleTile(icon: ImageVector, label: String, checked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun MmField(label: String, value: Float, modifier: Modifier, onValue: (Float) -> Unit) {
    var text by remember { mutableStateOf(value.toInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(4)
            text.toFloatOrNull()?.let { onValue(it.coerceIn(50f, 1000f)) }
        },
        label = { Text(label) },
        suffix = { Text("мм") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
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
