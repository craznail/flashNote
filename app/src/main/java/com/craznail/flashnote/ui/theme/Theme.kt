package com.craznail.flashnote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF3B82F6)
private val Green = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Green,
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    onSurface = Color(0xFF1F2937)
)

@Composable
fun FlashNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
