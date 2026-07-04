package dev.wolzard.recon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Status inks are app-level extensions, not M3 roles (design "Recon UI" 1a). */
@Immutable
data class StatusColors(val verified: Color, val unverified: Color, val unclear: Color, val bad: Color)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private val DarkStatus = StatusColors(Color(0xFFA8E06C), Color(0xFFE5B454), Color(0xFFA3ABA6), Color(0xFFE58A7E))
private val LightStatus = StatusColors(Color(0xFF3F6B12), Color(0xFF8A5A00), Color(0xFF5E6661), Color(0xFFA63A2E))

private val Lime = Color(0xFFA8E06C)
private val Ink = Color(0xFF0E1110)

// ponytail: system Sans/Monospace stand in for Inter/JetBrains Mono; bundle the fonts in res/font if exact match matters.
val Mono = FontFamily.Monospace
private val Sans = FontFamily.SansSerif

private val Dark = darkColorScheme(
