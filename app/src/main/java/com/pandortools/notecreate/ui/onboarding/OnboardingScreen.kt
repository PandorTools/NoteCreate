@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pandortools.notecreate.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.pandortools.notecreate.ui.common.IconChip
import com.pandortools.notecreate.ui.common.SegmentedChoice
import com.pandortools.notecreate.ui.library.ColorDot
import com.pandortools.notecreate.ui.library.FormSection
import com.pandortools.notecreate.ui.library.PaperOption
import kotlinx.coroutines.launch

/** Первый запуск: тип тетради, лист, бумага и рука. Всё можно поменять позже. */
@Composable
fun OnboardingScreen(onDone: (Notebook) -> Unit, onSkip: () -> Unit) {
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                TextButton(onClick = onSkip) { Text("Пропустить") }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(Modifier.size(80.dp))
                Spacer(Modifier.height(20.dp))
                Text("Первая тетрадь", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Выбери, как она будет выглядеть. Потом всё можно поменять.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }

            FormSection(Icons.AutoMirrored.Outlined.MenuBook, "Тип") {
                SegmentedChoice(
                    options = listOf(NotebookTemplate.DIARY, NotebookTemplate.NOTEBOOK),
                    selected = template, label = { it.title },
                    optionIcon = { if (it == NotebookTemplate.DIARY) Icons.Outlined.Today else Icons.AutoMirrored.Outlined.MenuBook },
                    onSelect = { template = it },
                )
            }

            FormSection(Icons.Outlined.AspectRatio, "Лист") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageFormat.entries.forEach { f -> IconChip(format == f, f.title) { format = f } }
                }
                if (format == PageFormat.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(customWidth.toInt().toString(), { customWidth = it.toFloatOrNull()?.coerceIn(50f, 1000f) ?: customWidth },
                            label = { Text("Ширина") }, suffix = { Text("мм") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(customHeight.toInt().toString(), { customHeight = it.toFloatOrNull()?.coerceIn(50f, 1000f) ?: customHeight },
                            label = { Text("Высота") }, suffix = { Text("мм") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
                SegmentedChoice(
                    options = Orientation.entries, selected = orientation, label = { it.title },
                    optionIcon = { if (it == Orientation.PORTRAIT) Icons.Outlined.CropPortrait else Icons.Outlined.CropLandscape },
                    onSelect = { orientation = it },
                )
            }

            FormSection(Icons.Outlined.GridOn, "Бумага") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperType.entries.forEach { p -> PaperOption(p, paperColor, paper == p) { paper = p } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Palette.paperColors.forEach { c -> ColorDot(c, paperColor == c) { paperColor = c } }
                }
            }

            FormSection(Icons.Outlined.SwapHoriz, "Рука") {
                SegmentedChoice(
                    options = listOf(false, true), selected = leftHanded,
                    label = { if (it) "Левша" else "Правша" },
                    onSelect = { leftHanded = it },
                )
            }
        }

        // ---------- Кнопка внизу ----------
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
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
                                    title = if (template == NotebookTemplate.DIARY) "Мой дневник" else "Моя тетрадь",
                                    template = template,
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 592.dp).fillMaxWidth().height(56.dp),
                ) {
                    Text("Начать писать", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}
