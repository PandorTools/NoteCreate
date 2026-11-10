@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pandortools.notecreate.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.Orientation
import com.pandortools.notecreate.data.PageFormat
import com.pandortools.notecreate.data.PageTurnStyle
import com.pandortools.notecreate.data.ThreeFingerAction
import com.pandortools.notecreate.data.StartScreen
import com.pandortools.notecreate.data.ThemeMode
import com.pandortools.notecreate.ui.common.AppLogo

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = AppGraph.settings
    val s by store.state.collectAsState()
    val context = LocalContext.current
    var section by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(section == 0, { section = 0 }, { Text("Письмо") }, leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) })
                    FilterChip(section == 1, { section = 1 }, { Text("Тетради") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp)) })
                    FilterChip(section == 2, { section = 2 }, { Text("Внешний вид") }, leadingIcon = { Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp)) })
                    FilterChip(section == 3, { section = 3 }, { Text("О приложении") }, leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)) })
                }
                when (section) {
                0 -> {
                Header("Письмо и стилус", Icons.Outlined.Edit)
                SwitchRow("Рисовать пальцем", "Иначе палец только листает и масштабирует, а пишет стилус", s.fingerDraw) {
                    store.update { st -> st.copy(fingerDraw = it) }
                }
                SwitchRow("Режим для левши", "Номер страницы и кнопки — с другой стороны", s.leftHanded) {
                    store.update { st -> st.copy(leftHanded = it) }
                }
                Choice("Тройное касание тремя пальцами", ThreeFingerAction.entries, s.threeFingerAction, { it.title }) {
                    v -> store.update { it.copy(threeFingerAction = v) }
                }
                Text("Быстро коснись листа тремя пальцами, чтобы выполнить выбранное действие. Щипок двумя пальцами масштабирует и перемещает страницу; длинный горизонтальный свайп перелистывает лист или создаёт следующий.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Кнопки стилуса настраиваются в системных настройках пера. " +
                    "Удержание нижней кнопки во время письма включает временный ластик.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                }
                1 -> {
                Header("Тетради и страницы", Icons.AutoMirrored.Outlined.MenuBook)
                Choice("Перелистывание", PageTurnStyle.entries, s.pageTurn, { it.title }) { v -> store.update { it.copy(pageTurn = v) } }
                Choice("При запуске открывать", StartScreen.entries, s.startScreen, { it.title }) { v -> store.update { it.copy(startScreen = v) } }
                SwitchRow("Дневник: новый день — новая страница", "Страница с сегодняшней датой создаётся сама", s.newDayNewPage) {
                    store.update { st -> st.copy(newDayNewPage = it) }
                }
                Choice("Формат по умолчанию", PageFormat.entries, s.defaultFormat, { it.title }) { v -> store.update { it.copy(defaultFormat = v) } }
                Choice("Ориентация по умолчанию", Orientation.entries, s.defaultOrientation, { it.title }) { v -> store.update { it.copy(defaultOrientation = v) } }

                }
                2 -> {
                Header("Внешний вид", Icons.Outlined.Palette)
                Choice("Тема", ThemeMode.entries, s.theme, { it.title }) { v -> store.update { it.copy(theme = v) } }
                }
                else -> {
                Header("Данные и приложение", Icons.Outlined.Info)
                Text("Все тетради хранятся только на этом устройстве, без облака и аккаунтов. " +
                    "Чтобы перенести записи, экспортируй тетрадь в PDF из меню тетради.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(Modifier.size(48.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("NoteCreate", style = MaterialTheme.typography.titleMedium)
                        Text("Версия ${appVersion(context)} · бесплатно и без рекламы", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PandorTools")))
                    }) { Text("GitHub") }
                }
                }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"

@Composable
private fun Header(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun <T> Choice(title: String, values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { v -> FilterChip(v == selected, { onSelect(v) }, { Text(label(v)) }) }
        }
    }
}
