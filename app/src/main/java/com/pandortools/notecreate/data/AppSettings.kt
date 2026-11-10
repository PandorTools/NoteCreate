package com.pandortools.notecreate.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PageTurnStyle(val title: String) { CURL("Сгиб листа"), SLIDE("Сдвиг"), NONE("Без анимации") }
enum class ThreeFingerAction(val title: String) { REDO("Повторить"), UNDO("Отменить"), NONE("Выключено") }
enum class StartScreen(val title: String) { LIBRARY("Библиотека"), LAST_NOTEBOOK("Последняя тетрадь") }
enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

data class SettingsState(
    val onboardingDone: Boolean = false,
    val defaultFormat: PageFormat = PageFormat.A5,
    val defaultOrientation: Orientation = Orientation.PORTRAIT,
    val defaultPaper: PaperType = PaperType.LINED,
    val defaultPaperColor: Int = Palette.paperColors[1],
    val leftHanded: Boolean = false,
    val fingerDraw: Boolean = false,
    val pageTurn: PageTurnStyle = PageTurnStyle.CURL,
    val threeFingerAction: ThreeFingerAction = ThreeFingerAction.REDO,
    val startScreen: StartScreen = StartScreen.LIBRARY,
    val newDayNewPage: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val favoriteColors: List<Int> = Palette.defaultFavorites,
    val recentColors: List<Int> = emptyList(),
    val notebookGroups: List<String> = emptyList(),
    val penWidths: Map<PenType, Float> = PenType.entries.associateWith { it.defaultWidth },
    val smoothing: Float = 0.35f,
    val highlighterOpacity: Float = 0.30f,
    val lastNotebookId: String? = null,
)

class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    val value: SettingsState get() = _state.value

    fun update(block: (SettingsState) -> SettingsState) {
        val new = block(_state.value)
        _state.value = new
        persist(new)
    }

    fun pushRecentColor(color: Int) = update { s ->
        s.copy(recentColors = (listOf(color) + s.recentColors.filter { it != color }).take(12))
    }

    private fun load(): SettingsState {
        val d = SettingsState()
        fun <E : Enum<E>> e(key: String, values: Array<E>, def: E): E =
            values.firstOrNull { it.name == prefs.getString(key, null) } ?: def
        fun ints(key: String, def: List<Int>): List<Int> =
            prefs.getString(key, null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() } ?: def
        return SettingsState(
            onboardingDone = prefs.getBoolean("onboardingDone", false),
            defaultFormat = e("defaultFormat", PageFormat.entries.toTypedArray(), d.defaultFormat),
            defaultOrientation = e("defaultOrientation", Orientation.entries.toTypedArray(), d.defaultOrientation),
            defaultPaper = e("defaultPaper", PaperType.entries.toTypedArray(), d.defaultPaper),
            defaultPaperColor = prefs.getInt("defaultPaperColor", d.defaultPaperColor),
            leftHanded = prefs.getBoolean("leftHanded", false),
            fingerDraw = prefs.getBoolean("fingerDraw", false),
            pageTurn = e("pageTurn", PageTurnStyle.entries.toTypedArray(), d.pageTurn),
            threeFingerAction = e("threeFingerAction", ThreeFingerAction.entries.toTypedArray(), d.threeFingerAction),
            startScreen = e("startScreen", StartScreen.entries.toTypedArray(), d.startScreen),
            newDayNewPage = prefs.getBoolean("newDayNewPage", true),
            theme = e("theme", ThemeMode.entries.toTypedArray(), d.theme),
            favoriteColors = ints("favoriteColors", d.favoriteColors),
            recentColors = ints("recentColors", emptyList()),
            notebookGroups = prefs.getString("notebookGroups", null)?.split('|')?.map(String::trim)?.filter(String::isNotEmpty)?.distinct().orEmpty(),
            penWidths = PenType.entries.associateWith { prefs.getFloat("width_" + it.name, it.defaultWidth) },
            smoothing = prefs.getFloat("smoothing", d.smoothing),
            highlighterOpacity = prefs.getFloat("highlighterOpacity", d.highlighterOpacity).coerceIn(0.08f, 0.8f),
            lastNotebookId = prefs.getString("lastNotebookId", null),
        )
    }

    private fun persist(s: SettingsState) = prefs.edit {
        putBoolean("onboardingDone", s.onboardingDone)
        putString("defaultFormat", s.defaultFormat.name)
        putString("defaultOrientation", s.defaultOrientation.name)
        putString("defaultPaper", s.defaultPaper.name)
        putInt("defaultPaperColor", s.defaultPaperColor)
        putBoolean("leftHanded", s.leftHanded)
        putBoolean("fingerDraw", s.fingerDraw)
        putString("pageTurn", s.pageTurn.name)
        putString("threeFingerAction", s.threeFingerAction.name)
        putString("startScreen", s.startScreen.name)
        putBoolean("newDayNewPage", s.newDayNewPage)
        putString("theme", s.theme.name)
        putString("favoriteColors", s.favoriteColors.joinToString(","))
        putString("recentColors", s.recentColors.joinToString(","))
        putString("notebookGroups", s.notebookGroups.joinToString("|"))
        s.penWidths.forEach { (k, v) -> putFloat("width_" + k.name, v) }
        putFloat("smoothing", s.smoothing)
        putFloat("highlighterOpacity", s.highlighterOpacity)
        putString("lastNotebookId", s.lastNotebookId)
    }
}
