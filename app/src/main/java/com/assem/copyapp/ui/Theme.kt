package com.assem.copyapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF0E1116),
    primaryContainer = Color(0xFF1C4F49),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF0E1116),
    tertiary = Color(0xFF8AB4F8),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFF2F5F7),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = Color(0xFF222933),
    onSurfaceVariant = Color(0xFF9BA6B2),
    error = Color(0xFFF87171),
    onError = Color(0xFF0E1116)
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF99F6E4),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1A73E8),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE7ECF0),
    onSurfaceVariant = Color(0xFF52606D),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun NewCopyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        content = content
    )
}
