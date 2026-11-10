@file:OptIn(ExperimentalLayoutApi::class)

package com.pandortools.notecreate.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pandortools.notecreate.data.Palette
import com.pandortools.notecreate.ui.library.ColorDot

private fun hsvToInt(h: Float, s: Float, v: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

/** Палитра с отдельными вкладками для точного выбора, готовых наборов и истории цветов. */
@Composable
fun ColorPickerDialog(initial: Int, recent: List<Int>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var section by remember { mutableIntStateOf(0) }
    var hex by remember { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) }
    val current = hsvToInt(hue, sat, value)

    fun setColor(c: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hex = String.format("%06X", c and 0xFFFFFF)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(0.96f).width(560.dp).heightIn(max = 760.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Цвет инструмента", style = MaterialTheme.typography.headlineSmall)
                        Text("Выбери оттенок или готовый цвет", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(current))
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)))
                    Column {
                        Text("Текущий цвет", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("#${String.format("%06X", current and 0xFFFFFF)}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TabRow(selectedTabIndex = section) {
                    listOf("Подбор", "Наборы", "Недавние").forEachIndexed { index, label ->
                        Tab(selected = section == index, onClick = { section = index }, text = { Text(label) })
                    }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (section) {
                        0 -> {
                            val hueColor = Color(hsvToInt(hue, 1f, 1f))
                            Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp))
                                .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                                .pointerInput(Unit) {
                                    fun update(pos: Offset) {
                                        sat = (pos.x / size.width).coerceIn(0f, 1f)
                                        value = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                                        hex = String.format("%06X", hsvToInt(hue, sat, value) and 0xFFFFFF)
                                    }
                                    awaitEachGesture {
                                        val down = awaitFirstDown(); update(down.position)
                                        drag(down.id) { update(it.position); it.consume() }
                                    }
                                }) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val p = Offset(sat * size.width, (1f - value) * size.height)
                                    drawCircle(Color.White, 11f, p, style = Stroke(3f))
                                    drawCircle(Color.Black.copy(alpha = 0.45f), 13f, p, style = Stroke(1f))
                                }
                            }
                            Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp))
                                .background(Brush.horizontalGradient(List(13) { Color(hsvToInt(it * 30f, 1f, 1f)) }))
                                .pointerInput(Unit) {
                                    fun update(pos: Offset) {
                                        hue = (pos.x / size.width).coerceIn(0f, 1f) * 360f
                                        hex = String.format("%06X", hsvToInt(hue, sat, value) and 0xFFFFFF)
                                    }
                                    awaitEachGesture {
                                        val down = awaitFirstDown(); update(down.position)
                                        drag(down.id) { update(it.position); it.consume() }
                                    }
                                }) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(Color.White, size.height / 2f - 2f,
                                        Offset(hue / 360f * size.width, size.height / 2f), style = Stroke(3f))
                                }
                            }
                            OutlinedTextField(value = hex, onValueChange = { text ->
                                val clean = text.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                                hex = clean
                                if (clean.length == 6) {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(0xFF000000.toInt() or clean.toInt(16), hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                }
                            }, prefix = { Text("#") }, singleLine = true, label = { Text("HEX") }, modifier = Modifier.fillMaxWidth())
                        }
                        1 -> {
                            Text("Готовые сочетания", style = MaterialTheme.typography.titleMedium)
                            Palette.presets.forEach { (name, colors) ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(name, style = MaterialTheme.typography.titleSmall)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            colors.forEach { c -> ColorDot(c, c == current, 38) { setColor(c) } }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text("Недавно выбранные", style = MaterialTheme.typography.titleMedium)
                            if (recent.isEmpty()) Text("Здесь появятся цвета, которые ты выберешь.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                recent.forEach { c -> ColorDot(c, c == current, 42) { setColor(c) } }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onPick(current) }) { Text("Выбрать цвет") }
                }
            }
        }
    }
}
