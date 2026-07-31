package com.xanichka.xacode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val XaGreen = Color(0xFF9AE66E)
val XaBackground = Color(0xFF111110)
val XaSurface = Color(0xFF1A1A18)
val XaSurfaceHigh = Color(0xFF242421)
val XaText = Color(0xFFF4F3ED)
val XaMuted = Color(0xFFA7A69E)

private val XaCodeColors = darkColorScheme(
    primary = XaGreen,
    onPrimary = Color(0xFF13200D),
    primaryContainer = Color(0xFF283A20),
    onPrimaryContainer = Color(0xFFC8F5AD),
    background = XaBackground,
    onBackground = XaText,
    surface = XaSurface,
    onSurface = XaText,
    surfaceVariant = XaSurfaceHigh,
    onSurfaceVariant = XaMuted,
    outline = Color(0xFF3B3B37),
    error = Color(0xFFFF8A80)
)

@Composable
fun XaCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = XaCodeColors, content = content)
}

