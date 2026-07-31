package com.xanichka.xacode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val XaBlue = Color(0xFF4C86F7)
val XaBackground = Color(0xFF0C0E12)
val XaSurface = Color(0xFF17191E)
val XaSurfaceHigh = Color(0xFF22252C)
val XaText = Color(0xFFF4F6FA)
val XaMuted = Color(0xFF969BA6)

private val XaCodeColors = darkColorScheme(
    primary = XaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF193661),
    onPrimaryContainer = Color(0xFFD8E7FF),
    background = XaBackground,
    onBackground = XaText,
    surface = XaSurface,
    onSurface = XaText,
    surfaceVariant = XaSurfaceHigh,
    onSurfaceVariant = XaMuted,
    outline = Color(0xFF353941),
    error = Color(0xFFFF8A80)
)

@Composable
fun XaCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = XaCodeColors, content = content)
}
