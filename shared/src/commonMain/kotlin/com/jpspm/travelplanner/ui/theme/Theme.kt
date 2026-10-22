package com.jpspm.travelplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF8B5000),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF2E6A44),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB1F1C1),
    onTertiaryContainer = Color(0xFF00210E),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerLow = Color(0xFFF2F3F7),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFFFB870),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3B00),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFF96D5A6),
    onTertiary = Color(0xFF00391D),
    tertiaryContainer = Color(0xFF13512E),
    onTertiaryContainer = Color(0xFFB1F1C1),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainer = Color(0xFF1D2023),
    surfaceContainerHigh = Color(0xFF272A2D),
    surfaceContainerLow = Color(0xFF191C1E),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun TravelPlannerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
