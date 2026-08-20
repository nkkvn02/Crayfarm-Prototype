package com.example.crayfarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CrayFarmPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = CrayFarmPrimaryDark,
    secondary = CrayFarmSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E6FF),
    onSecondaryContainer = Color(0xFF18286F),
    tertiary = CrayFarmAccent,
    background = CrayFarmBackground,
    onBackground = CrayFarmText,
    surface = CrayFarmSurface,
    onSurface = CrayFarmText,
    surfaceVariant = CrayFarmSurfaceVariant,
    onSurfaceVariant = CrayFarmTextSecondary,
    error = CrayFarmError,
    onError = Color.White,
    outline = Color(0xFF78809A),
    outlineVariant = Color(0xFFD4D8E5)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    onPrimary = Color(0xFF00105C),
    primaryContainer = Color(0xFF1E2E91),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFF74DBC4),
    onSecondary = Color(0xFF00382F),
    background = Color(0xFF10121A),
    onBackground = Color(0xFFE5E7F1),
    surface = Color(0xFF181A23),
    onSurface = Color(0xFFE5E7F1),
    surfaceVariant = Color(0xFF292C38),
    onSurfaceVariant = Color(0xFFC6C9D7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun CrayFarmTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
