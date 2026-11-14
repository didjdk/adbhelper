package com.rel.adbhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ADB Helper's palette: a dark-indigo, single-accent "oscilloscope at rest" identity — one
 * signal-cyan accent against a cool indigo structure, not the warm cream/terracotta or flat
 * near-black/acid-green defaults. Monospace is reserved for real device data (addresses,
 * serials, command output) elsewhere in the UI, never used decoratively.
 */
private val DeepSpace = Color(0xFF10131C)
private val Indigo = Color(0xFF2A3356)
private val IndigoLight = Color(0xFF3C4670)
private val SignalCyan = Color(0xFF27E1C1)
private val Paper = Color(0xFFF2F4F8)
private val Graphite = Color(0xFF1B1F2A)
private val AmberCaution = Color(0xFFFFA63E)

private val DarkColors = darkColorScheme(
    primary = SignalCyan,
    onPrimary = DeepSpace,
    secondary = IndigoLight,
    onSecondary = Color.White,
    tertiary = AmberCaution,
    onTertiary = DeepSpace,
    background = DeepSpace,
    onBackground = Color(0xFFE4E8F1),
    surface = Indigo,
    onSurface = Color(0xFFE4E8F1),
    surfaceVariant = IndigoLight,
    onSurfaceVariant = Color(0xFFC7CEE3),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E8577),
    onPrimary = Color.White,
    secondary = Indigo,
    onSecondary = Color.White,
    tertiary = Color(0xFFB5680A),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Graphite,
    surface = Color.White,
    onSurface = Graphite,
    surfaceVariant = Color(0xFFE3E7F0),
    onSurfaceVariant = Color(0xFF454B5C),
)

/** Reserved for device data — addresses, serials, command output — never for decoration. */
val DataText = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/**
 * Always dark by default, regardless of the system theme: the dark-indigo "oscilloscope at
 * rest" look above is this app's actual visual identity (the light scheme just mirrors the same
 * roles), not a night-mode variant — following [isSystemInDarkTheme] here showed the app in its
 * light scheme on a light-mode device/emulator, not the identity it was designed around.
 */
@Composable
fun AdbHelperTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
