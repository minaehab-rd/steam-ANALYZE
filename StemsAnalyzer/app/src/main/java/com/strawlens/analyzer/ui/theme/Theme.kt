package com.strawlens.analyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StemGreen = Color(0xFF2E7D32)
private val ProductGold = Color(0xFFF5A623)

private val LightColors = lightColorScheme(
    primary = StemGreen,
    secondary = ProductGold,
    tertiary = ProductGold
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = ProductGold,
    tertiary = ProductGold
)

@Composable
fun StemsAnalyzerTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

val stemColor = StemGreen
val productColor = ProductGold
