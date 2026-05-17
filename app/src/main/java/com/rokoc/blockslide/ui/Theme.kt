package com.rokoc.blockslide.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF1E6F5C),
    onPrimary = Color.White,
    secondary = Color(0xFF4C6FBF),
    onSecondary = Color.White,
    tertiary = Color(0xFFE7B10A),
    background = Color(0xFFF5F6F1),
    onBackground = Color(0xFF18221F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18221F),
    surfaceVariant = Color(0xFFE0E6DE),
    onSurfaceVariant = Color(0xFF3E4B45),
)

@Composable
fun BlockSlideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
