package com.craznail.flashnote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FlashPrimary = Color(0xFF3B82F6)
val FlashBackground = Color(0xFFF8FAFC)
val FlashSuccess = Color(0xFF22C55E)
val FlashError = Color(0xFFEF4444)
val FlashIndigo = Color(0xFF6366F1)
val FlashPurple = Color(0xFF8B5CF6)
val FlashOnSurfaceMuted = Color(0xFF6B7280)
val FlashCard = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = FlashPrimary,
    onPrimary = Color.White,
    secondary = FlashSuccess,
    background = FlashBackground,
    surface = FlashCard,
    onSurface = Color(0xFF111827),
    error = FlashError,
    onError = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = FlashOnSurfaceMuted
)

@Composable
fun FlashNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
