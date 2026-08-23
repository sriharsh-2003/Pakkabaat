package com.pakkabaat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PakkaSaffron = Color(0xFFFF6D00)
val PakkaGreen = Color(0xFF1B5E20)
val PakkaInk = Color(0xFF1A1A1A)

private val LightColors = lightColorScheme(
    primary = PakkaSaffron,
    secondary = PakkaGreen,
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = PakkaSaffron,
    secondary = PakkaGreen
)

@Composable
fun PakkaBaatTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
