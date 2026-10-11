package com.example.xdownloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackColorScheme = darkColorScheme(
    primary = Color(0xFF1D9BF0), // Twitter / X Accent Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF15202B),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF1D9BF0),
    onSecondary = Color.White,
    background = Color(0xFF000000), // Pure OLED dark
    onBackground = Color(0xFFF7F9F9),
    surface = Color(0xFF16181C),
    onSurface = Color(0xFFF7F9F9),
    surfaceVariant = Color(0xFF202327),
    onSurfaceVariant = Color(0xFF71767B)
)

@Composable
fun XDownloaderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlackColorScheme,
        content = content
    )
}
