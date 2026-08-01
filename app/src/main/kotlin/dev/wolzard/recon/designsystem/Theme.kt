package dev.wolzard.recon.designsystem

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
    background = Ink, surface = Ink, onBackground = Color(0xFFE8ECE9), onSurface = Color(0xFFE8ECE9),
    surfaceContainer = Color(0xFF161A18), surfaceContainerHigh = Color(0xFF1F2421),
    outlineVariant = Color(0xFF2C322F), outline = Color(0xFF2C322F), onSurfaceVariant = Color(0xFF9BA39E),
    primary = Lime, onPrimary = Ink, error = DarkStatus.bad, onError = Ink,
)

private val Light = lightColorScheme(
    background = Color(0xFFF4F5F2), surface = Color(0xFFF4F5F2), onBackground = Color(0xFF141816), onSurface = Color(0xFF141816),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFECEEEA),
    outlineVariant = Color(0xFFD5D9D4), outline = Color(0xFFD5D9D4), onSurfaceVariant = Color(0xFF545C57),
    primary = Lime, onPrimary = Ink, error = LightStatus.bad, onError = Color.White,
)

private fun sans(w: FontWeight, size: Int, line: Int) = TextStyle(fontFamily = Sans, fontWeight = w, fontSize = size.sp, lineHeight = line.sp)

private val ReconTypography = Typography(
    titleLarge = sans(FontWeight.SemiBold, 22, 28),
    titleMedium = sans(FontWeight.SemiBold, 17, 22),
    bodyLarge = sans(FontWeight.Normal, 15, 23),
    bodyMedium = sans(FontWeight.Normal, 14, 20),
    labelLarge = sans(FontWeight.SemiBold, 15, 20),
    bodySmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.1.em),
)

private val ReconShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(12.dp),
)

val ColorScheme.status: StatusColors @Composable get() = LocalStatusColors.current

// ponytail: dynamic colour dropped on purpose; the design is a fixed palette.
@Composable
fun ReconTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = ReconTypography, shapes = ReconShapes, content = content)
    }
}
