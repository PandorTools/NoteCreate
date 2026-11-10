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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.pandortools.notecreate.data.Palette
import com.pandortools.notecreate.ui.library.ColorDot

private fun hsvToInt(h: Float, s: Float, v: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

/** Полный выбор цвета: квадрат насыщенности/яркости, полоса оттенка, HEX, готовые наборы и недавние. */
@Composable
fun ColorPickerDialog(
    initial: Int,
    recent: List<Int>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    val current = hsvToInt(hue, sat, value)
    var hex by remember { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) }

    fun setColor(c: Int) {
        val t = FloatArray(3); android.graphics.Color.colorToHSV(c, t)
        hue = t[0]; sat = t[1]; value = t[2]
        hex = String.format("%06X", c and 0xFFFFFF)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(
                Modifier.padding(22.dp).width(380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Палитра", style = MaterialTheme.typography.headlineSmall)
                        Text("Цвет текущего инструмента", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Закрыть") }
                }
                // Насыщенность × яркость
                val hueColor = Color(hsvToInt(hue, 1f, 1f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .pointerInput(Unit) {
                            fun upd(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                hex = String.format("%06X", hsvToInt(hue, sat, value) and 0xFFFFFF)
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                upd(down.position)
                                drag(down.id) { ch -> upd(ch.position); ch.consume() }
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSizeCompat()) {
                        val c = Offset(sat * size.width, (1f - value) * size.height)
                        drawCircle(Color.White, 11f, c, style = Stroke(3f))
                        drawCircle(Color.Black.copy(alpha = 0.4f), 13f, c, style = Stroke(1f))
                    }
                }
                // Оттенок
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(List(13) { Color(hsvToInt(it * 30f, 1f, 1f)) }))
                        .pointerInput(Unit) {
                            fun upd(o: Offset) {
                                hue = (o.x / size.width).coerceIn(0f, 1f) * 360f
                                hex = String.format("%06X", hsvToInt(hue, sat, value) and 0xFFFFFF)
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                upd(down.position)
                                drag(down.id) { ch -> upd(ch.position); ch.consume() }
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSizeCompat()) {
                        val x = hue / 360f * size.width
                        drawCircle(Color.White, size.height / 2f - 2f, Offset(x, size.height / 2f), style = Stroke(3f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(current))
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { t ->
                            val clean = t.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            hex = clean
                            if (clean.length == 6) {
                                val c = (0xFF000000.toInt()) or clean.toInt(16)
                                val tmp = FloatArray(3); android.graphics.Color.colorToHSV(c, tmp)
                                hue = tmp[0]; sat = tmp[1]; value = tmp[2]
                            }
                        },
                        prefix = { Text("#") },
                        singleLine = true,
                        label = { Text("HEX") },
                    )
                }
                if (recent.isNotEmpty()) {
                    Text("Недавние цвета", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { c -> ColorDot(c, c == current, 36) { setColor(c) } }
                    }
                }
                Palette.presets.forEach { (name, colors) ->
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { c -> ColorDot(c, c == current, 36) { setColor(c) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onPick(current) }) { Text("Выбрать") }
                }
            }
        }
    }
}

/** Canvas на весь родительский Box. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()
