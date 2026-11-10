@file:OptIn(ExperimentalMaterial3Api::class)

package com.pandortools.notecreate.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.CoverMaterial
import com.pandortools.notecreate.data.Notebook
import com.pandortools.notecreate.data.NotebookSpec
import com.pandortools.notecreate.data.NotebookTemplate
import com.pandortools.notecreate.data.Orientation
import com.pandortools.notecreate.data.PageFormat
import com.pandortools.notecreate.data.Palette
import com.pandortools.notecreate.data.PaperType
import com.pandortools.notecreate.ui.common.AppLogo
import com.pandortools.notecreate.ui.library.ColorDot
import com.pandortools.notecreate.ui.library.PaperOption
import com.pandortools.notecreate.ui.library.Section
import com.pandortools.notecreate.ui.library.drawPaperIcon
import kotlinx.coroutines.launch

/** Первый запуск: пользователь сам выбирает лист, ориентацию, бумагу и руку. */
@Composable
fun OnboardingScreen(onDone: (Notebook) -> Unit) {
    val settings = AppGraph.settings
    val s0 = settings.value
    var format by remember { mutableStateOf(s0.defaultFormat) }
    var customWidth by remember { mutableFloatStateOf(210f) }
    var customHeight by remember { mutableFloatStateOf(297f) }
    var template by remember { mutableStateOf(NotebookTemplate.DIARY) }
    var orientation by remember { mutableStateOf(s0.defaultOrientation) }
    var paper by remember { mutableStateOf(s0.defaultPaper) }
    var paperColor by remember { mutableIntStateOf(s0.defaultPaperColor) }
    var leftHanded by remember { mutableStateOf(s0.leftHanded) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppLogo(Modifier.size(84.dp).align(Alignment.CenterHorizontally))
            Text("Добро пожаловать в NoteCreate", style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Настроим тетрадь под тебя. Всё это можно поменять потом для каждой тетради.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Три шага — и можно писать", style = MaterialTheme.typography.titleMedium)
                    Text("Выбери тип тетради, формат листа и разметку. Записи сохраняются только на этом устройстве.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f))
                }
            }
            Section("Что создаём") {
                NotebookTemplate.entries.forEach { t -> FilterChip(template == t, { template = t }, { Text(t.title) }) }
            }
            Section("Формат листа") {
                PageFormat.entries.forEach { f -> FilterChip(format == f, { format = f }, { Text(f.title) }) }
            }
            if (format == PageFormat.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(customWidth.toInt().toString(), { customWidth = it.toFloatOrNull()?.coerceIn(50f, 1000f) ?: customWidth },
                        label = { Text("Ширина, мм") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(customHeight.toInt().toString(), { customHeight = it.toFloatOrNull()?.coerceIn(50f, 1000f) ?: customHeight },
                        label = { Text("Высота, мм") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Canvas(Modifier.size(62.dp, 84.dp)) {
                    drawPaperIcon(paper, paperColor)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Предпросмотр", style = MaterialTheme.typography.titleSmall)
                    Text("${format.title} · ${if (orientation == Orientation.PORTRAIT) format.widthMm.toInt() else format.heightMm.toInt()} × ${if (orientation == Orientation.PORTRAIT) format.heightMm.toInt() else format.widthMm.toInt()} мм",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("${paper.title} · ${orientation.title}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Section("Как держишь планшет, когда пишешь") {
                Orientation.entries.forEach { o -> FilterChip(orientation == o, { orientation = o }, { Text(o.title) }) }
            }
            Section("Бумага") {
                PaperType.entries.forEach { p -> PaperOption(p, paperColor, paper == p) { paper = p } }
            }
            Section("Цвет бумаги") {
                Palette.paperColors.forEach { c -> ColorDot(c, paperColor == c) { paperColor = c } }
            }
            Section("Рука") {
                FilterChip(!leftHanded, { leftHanded = false }, { Text("Правша") })
                FilterChip(leftHanded, { leftHanded = true }, { Text("Левша") })
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    settings.update {
                        it.copy(
                            onboardingDone = true, defaultFormat = format, defaultOrientation = orientation,
                            defaultPaper = paper, defaultPaperColor = paperColor, leftHanded = leftHanded,
                        )
                    }
                    scope.launch {
                        val nb = AppGraph.repository.create(
                            NotebookSpec(
                                title = if (template == NotebookTemplate.DIARY) "Мой дневник" else "Моя тетрадь", template = template,
                                coverColor = Palette.coverColors[0], coverMaterial = CoverMaterial.LEATHER,
                                elastic = true, ribbon = true, paperType = paper, paperColor = paperColor,
                                format = format, orientation = orientation,
                                customWidthMm = if (format == PageFormat.CUSTOM) customWidth else null,
                                customHeightMm = if (format == PageFormat.CUSTOM) customHeight else null,
                            )
                        )
                        onDone(nb)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Начать писать") }
        }
    }
}
