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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BackHand
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.pandortools.notecreate.data.AppGraph
import com.pandortools.notecreate.data.Orientation
import com.pandortools.notecreate.data.PageFormat
import com.pandortools.notecreate.data.PageTurnStyle
import com.pandortools.notecreate.data.StartScreen
import com.pandortools.notecreate.data.ThemeMode
import com.pandortools.notecreate.data.ThreeFingerAction
import com.pandortools.notecreate.ui.common.AppLogo
import com.pandortools.notecreate.ui.common.IconChip
import com.pandortools.notecreate.ui.common.RowDivider
import com.pandortools.notecreate.ui.common.SegmentedRow
import com.pandortools.notecreate.ui.common.SettingRow
import com.pandortools.notecreate.ui.common.SettingsCard
import com.pandortools.notecreate.ui.common.SliderRow
import com.pandortools.notecreate.ui.common.SwitchRow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = AppGraph.settings
    val s by store.state.collectAsState()
    val context = LocalContext.current
    val notebooks by AppGraph.repository.notebooks.collectAsState()
    val scope = rememberCoroutineScope()
    val exportTarget = remember { androidx.compose.runtime.mutableStateOf<com.pandortools.notecreate.data.Notebook?>(null) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val target = exportTarget.value
        if (uri != null && target != null) scope.launch {
            val ok = AppGraph.repository.exportNotebook(target, uri)
            Toast.makeText(context, if (ok) "Резервная копия сохранена" else "Не удалось сохранить копию", Toast.LENGTH_LONG).show()
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val nb = AppGraph.repository.importNotebook(uri)
            Toast.makeText(context, if (nb != null) "Тетрадь «${nb.title}» восстановлена" else "Не удалось открыть архив .ncbook", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SettingsCard("Письмо и стилус") {
                    SwitchRow(Icons.Outlined.TouchApp, "Рисовать пальцем",
                        "Без этого палец только листает и масштабирует", s.fingerDraw) {
                        store.update { st -> st.copy(fingerDraw = it) }
                    }
                    RowDivider()
                    SwitchRow(Icons.Outlined.SwapHoriz, "Режим для левши",
                        "Кнопки страниц переедут на правую сторону", s.leftHanded) {
                        store.update { st -> st.copy(leftHanded = it) }
                    }
                    RowDivider()
                    SliderRow(Icons.Outlined.Gesture, "Сглаживание линии", "${(s.smoothing * 100).roundToInt()}%",
                        s.smoothing, 0f..0.9f) { v -> store.update { it.copy(smoothing = v) } }
                    RowDivider()
                    SegmentedRow(
                        Icons.Outlined.BackHand, "Касание тремя пальцами",
                        ThreeFingerAction.entries, s.threeFingerAction, { it.title },
                    ) { v -> store.update { it.copy(threeFingerAction = v) } }
                    RowDivider()
                    SettingRow(Icons.Outlined.Edit, "Кнопка стилуса",
                        "Удерживай её во время письма — включится ластик. Назначение кнопок меняется в настройках пера Android.")
                }

                SettingsCard("Тетради") {
                    SegmentedRow(
                        Icons.AutoMirrored.Outlined.MenuBook, "Перелистывание",
                        PageTurnStyle.entries, s.pageTurn, { it.title },
                    ) { v -> store.update { it.copy(pageTurn = v) } }
                    RowDivider()
                    SegmentedRow(
                        Icons.Outlined.Home, "При запуске открывать",
                        StartScreen.entries, s.startScreen, { it.title },
                    ) { v -> store.update { it.copy(startScreen = v) } }
                    RowDivider()
                    SwitchRow(Icons.Outlined.Today, "Новый день — новая страница",
                        "Для дневников: страница с сегодняшней датой появится сама", s.newDayNewPage) {
                        store.update { st -> st.copy(newDayNewPage = it) }
                    }
                    RowDivider()
                    Column(Modifier.padding(bottom = 12.dp)) {
                        SettingRow(Icons.Outlined.AspectRatio, "Формат новых тетрадей", s.defaultFormat.title)
                        FlowRow(
                            Modifier.padding(start = 70.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PageFormat.entries.filter { it != PageFormat.CUSTOM }.forEach { f ->
                                IconChip(f == s.defaultFormat, f.title) { store.update { it.copy(defaultFormat = f) } }
                            }
                        }
                    }
                    RowDivider()
                    SegmentedRow(
                        Icons.Outlined.CropPortrait, "Ориентация новых тетрадей",
                        Orientation.entries, s.defaultOrientation, { it.title },
                        optionIcon = { if (it == Orientation.PORTRAIT) Icons.Outlined.CropPortrait else Icons.Outlined.CropLandscape },
                    ) { v -> store.update { it.copy(defaultOrientation = v) } }
                }

                SettingsCard("Оформление") {
                    SegmentedRow(
                        Icons.Outlined.Brightness6, "Тема",
                        ThemeMode.entries, s.theme, { it.title },
                        optionIcon = {
                            when (it) {
                                ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                            }
                        },
                    ) { v -> store.update { it.copy(theme = v) } }
                }

                SettingsCard("Данные") {
                    SettingRow(Icons.Outlined.PhoneAndroid, "Хранятся на устройстве",
                        "Без облака и аккаунтов. Никто, кроме тебя, их не видит.")
                    RowDivider()
                    SettingRow(Icons.Outlined.PictureAsPdf, "Восстановить .ncbook",
                        "Открой резервную копию тетради с устройства", onClick = { importBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
                    notebooks.filter { it.deletedAt == null }.forEach { notebook ->
                        RowDivider()
                        SettingRow(Icons.AutoMirrored.Outlined.MenuBook, "Сохранить «${notebook.title}»",
                            "Создать переносимый архив .ncbook", onClick = {
                                exportTarget.value = notebook
                                exportBackup.launch("${notebook.title}.ncbook")
                            })
                    }
                }

                SettingsCard("О приложении") {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(Modifier.size(52.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("NoteCreate", style = MaterialTheme.typography.titleMedium)
                            Text("Версия ${appVersion(context)} · бесплатно и без рекламы",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    RowDivider()
                    SettingRow(Icons.Outlined.Code, "Исходный код", "github.com/PandorTools/NoteCreate",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PandorTools/NoteCreate")))
                        }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RowDivider()
                    SettingRow(Icons.Outlined.Gavel, "Лицензия", "MIT — можно пользоваться и изменять свободно")
                    RowDivider()
                    SettingRow(Icons.Outlined.Info, "Отзывы и идеи", "Пиши в Issues на GitHub")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun appVersion(context: android.content.Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
