package io.github.ibuds.sfiles

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class Palette(
    val bg: Color, val card: Color, val pill: Color, val sheet: Color, val menu: Color,
    val divider: Color, val muted: Color, val accent: Color, val bar: Color, val chip: Color, val tile: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF000000), card = Color(0xFF171717), pill = Color(0xFF1F1F1F), sheet = Color(0xFF2B2B2B), menu = Color(0xFF2C2C2E),
    divider = Color(0xFF3A3A3A), muted = Color(0xFFA0A0A0), accent = Color(0xFF4C93FF), bar = Color(0xFF262626), chip = Color(0xFF3A3A3A), tile = Color(0xFF262626),
)
val LightPalette = Palette(
    bg = Color(0xFFF4F4F6), card = Color(0xFFFFFFFF), pill = Color(0xFFFFFFFF), sheet = Color(0xFFF2F2F5), menu = Color(0xFFFFFFFF),
    divider = Color(0xFFDADADF), muted = Color(0xFF6E6E73), accent = Color(0xFF1F6FEB), bar = Color(0xFFFFFFFF), chip = Color(0xFFE3E4E9), tile = Color(0xFFEEEFF3),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val Bg: Color @Composable get() = LocalPalette.current.bg
val Card: Color @Composable get() = LocalPalette.current.card
val Pill: Color @Composable get() = LocalPalette.current.pill
val Sheet: Color @Composable get() = LocalPalette.current.sheet
val MenuBg: Color @Composable get() = LocalPalette.current.menu
val Divider: Color @Composable get() = LocalPalette.current.divider
val Muted: Color @Composable get() = LocalPalette.current.muted
val Accent: Color @Composable get() = LocalPalette.current.accent
val Bar: Color @Composable get() = LocalPalette.current.bar
val Chip: Color @Composable get() = LocalPalette.current.chip
val Tile: Color @Composable get() = LocalPalette.current.tile

@Composable
fun SFilesTheme(content: @Composable () -> Unit) {
    val dark = when (Fs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        background = p.bg, surface = p.bg, primary = p.accent, onBackground = Color.White, onSurface = Color.White, surfaceContainer = p.sheet,
    ) else lightColorScheme(
        background = p.bg, surface = p.bg, primary = p.accent, onBackground = Color(0xFF111114), onSurface = Color(0xFF111114), surfaceContainer = p.sheet,
    )
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}
