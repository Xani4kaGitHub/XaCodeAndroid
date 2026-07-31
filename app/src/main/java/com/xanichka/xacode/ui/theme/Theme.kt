package com.xanichka.xacode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val XaBlue = Color(0xFFCBA6F7)
val XaBackground = Color(0xFF1E1E2E)
val XaSurface = Color(0xFF272736)
val XaSurfaceHigh = Color(0xFF353543)
val XaText = Color(0xFFCDD6F4)
val XaMuted = Color(0xFF9096AF)

private val XaCodeColors = darkColorScheme(
    primary = XaBlue,
    onPrimary = Color(0xFF1E1E2E),
    primaryContainer = Color(0xFF3B3151),
    onPrimaryContainer = Color(0xFFE7D5FF),
    background = XaBackground,
    onBackground = XaText,
    surface = XaSurface,
    onSurface = XaText,
    surfaceVariant = XaSurfaceHigh,
    onSurfaceVariant = XaMuted,
    outline = Color(0xFF42424F),
    error = Color(0xFFF38BA8)
)

@Composable
fun XaCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = XaCodeColors, content = content)
}
