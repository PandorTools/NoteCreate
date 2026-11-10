package com.pandortools.notecreate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandortools.notecreate.data.ThemeMode

val Ink = Color(0xFF1E2A44)
val Accent = Color(0xFFE8734A)
val Cream = Color(0xFFF6F1E7)
val PagesBeige = Color(0xFFDDD3BF)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    primaryContainer = Color(0xFFD9E0EE),
    onPrimaryContainer = Ink,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCD),
    onSecondaryContainer = Color(0xFF3A0B00),
    tertiary = Color(0xFF3E6259),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE3D5),
    onSurfaceVariant = Color(0xFF4A4F5C),
    surfaceContainer = Color(0xFFF0EADD),
    surfaceContainerHigh = Color(0xFFEBE4D6),
    surfaceContainerHighest = Color(0xFFE5DECF),
    surfaceContainerLow = Color(0xFFF3EEE3),
    outline = Color(0xFF8A8577),
    outlineVariant = Color(0xFFD3CCBC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C6E6),
    onPrimary = Color(0xFF1B2A47),
    primaryContainer = Color(0xFF33405C),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Accent,
    onSecondary = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFF6E2E16),
    onSecondaryContainer = Color(0xFFFFDBCD),
    tertiary = Color(0xFFA5CEC2),
    background = Color(0xFF14161C),
    onBackground = Color(0xFFE4E2DC),
    surface = Color(0xFF14161C),
    onSurface = Color(0xFFE4E2DC),
    surfaceVariant = Color(0xFF2A2D35),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceContainer = Color(0xFF1E2128),
    surfaceContainerHigh = Color(0xFF252831),
    surfaceContainerHighest = Color(0xFF2E323B),
    surfaceContainerLow = Color(0xFF1A1C22),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF3F434C),
)

@Composable
fun NoteCreateTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
