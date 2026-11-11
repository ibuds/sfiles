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
import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class Palette(
    val bg: Color, val card: Color, val pill: Color, val sheet: Color, val menu: Color,
    val divider: Color, val muted: Color, val accent: Color, val bar: Color, val chip: Color, val tile: Color,
    val tint: Color = tile, val fg: Color? = null,
)

val DarkPalette = Palette(
    bg = Color(0xFF000000), card = Color(0xFF171717), pill = Color(0xFF1F1F1F), sheet = Color(0xFF2B2B2B), menu = Color(0xFF2C2C2E),
    divider = Color(0xFF3A3A3A), muted = Color(0xFFA0A0A0), accent = Color(0xFF4C93FF), bar = Color(0xFF262626), chip = Color(0xFF3A3A3A), tile = Color(0xFF262626),
)
val LightPalette = Palette(
    bg = Color(0xFFF4F4F6), card = Color(0xFFFFFFFF), pill = Color(0xFFFFFFFF), sheet = Color(0xFFF2F2F5), menu = Color(0xFFFFFFFF),
    divider = Color(0xFFDADADF), muted = Color(0xFF6E6E73), accent = Color(0xFF1F6FEB), bar = Color(0xFFFFFFFF), chip = Color(0xFFE3E4E9), tile = Color(0xFFEEEFF3),
)

/** The minimalist style: calm teal on soft green-grey, with tinted icon squares. */
val MinDarkPalette = Palette(
    bg = Color(0xFF0F1312), card = Color(0xFF181D1C), pill = Color(0xFF1D2322), sheet = Color(0xFF1D2322), menu = Color(0xFF1D2322),
    divider = Color(0xFF262D2B), muted = Color(0xFF8D9A96), accent = Color(0xFF5FD0BB), bar = Color(0xFF1D2322), chip = Color(0xFF242B29), tile = Color(0xFF1F3A35),
    tint = Color(0xFF1F3A35), fg = Color(0xFFE8EEEC),
)
val MinLightPalette = Palette(
    bg = Color(0xFFF5F7F6), card = Color(0xFFFFFFFF), pill = Color(0xFFFFFFFF), sheet = Color(0xFFFFFFFF), menu = Color(0xFFFFFFFF),
    divider = Color(0xFFE3E8E6), muted = Color(0xFF6B7773), accent = Color(0xFF0F7B6C), bar = Color(0xFFFFFFFF), chip = Color(0xFFE8EEEC), tile = Color(0xFFDFF1ED),
    tint = Color(0xFFDFF1ED), fg = Color(0xFF14201D),
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
val Tint: Color @Composable get() = LocalPalette.current.tint

private val Roboto = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold),
)

/**
 * Some phone makers (vivo/iQOO, among others) apply their font by patching the classic TextView instead of the default
 * typeface, which Compose never sees. A TextView created here picks it up, so its typeface is reused for Compose.
 */
private fun probeSystemTypeface(ctx: Context): Typeface? {
    val tv = android.widget.TextView(ctx)
    val tf = tv.typeface ?: tv.paint.typeface
    return if (tf == null || tf == Typeface.DEFAULT || tf == Typeface.SANS_SERIF) null else tf
}

private class SysFont(val tf: Typeface, override val weight: FontWeight) : AndroidFont(
    FontLoadingStrategy.Blocking, SysFontLoader, FontVariation.Settings(),
) {
    override val style: FontStyle = FontStyle.Normal
}

private object SysFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = (font as SysFont).tf
    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? = (font as SysFont).tf
}

private fun deviceFamily(base: Typeface): FontFamily {
    fun at(w: Int, fallbackStyle: Int) =
        if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(base, w, false) else Typeface.create(base, fallbackStyle)
    return FontFamily(
        SysFont(at(400, Typeface.NORMAL), FontWeight.Normal),
        SysFont(at(500, Typeface.NORMAL), FontWeight.Medium),
        SysFont(at(600, Typeface.BOLD), FontWeight.SemiBold),
        SysFont(at(700, Typeface.BOLD), FontWeight.Bold),
    )
}

/** Material's default tracking and fixed line heights are not what the system uses, so both follow the font instead. */
private fun typography(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family, letterSpacing = 0.sp, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

@Composable
fun SFilesTheme(content: @Composable () -> Unit) {
    val dark = when (Fs.themeMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val p = if (Fs.minimal) (if (dark) MinDarkPalette else MinLightPalette) else if (dark) DarkPalette else LightPalette
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val device = remember { probeSystemTypeface(ctx)?.also { Fs.systemFontCustom = true }?.let(::deviceFamily) }
    val scheme = if (dark) darkColorScheme(
        background = p.bg, surface = p.bg, primary = p.accent, onBackground = p.fg ?: Color.White, onSurface = p.fg ?: Color.White, surfaceContainer = p.sheet,
    ) else lightColorScheme(
        background = p.bg, surface = p.bg, primary = p.accent, onBackground = p.fg ?: Color(0xFF111114), onSurface = p.fg ?: Color(0xFF111114), surfaceContainer = p.sheet,
    )
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    CompositionLocalProvider(LocalPalette provides p) { MaterialTheme(colorScheme = scheme, typography = typography(if (Fs.fontMode == 1) Roboto else device ?: FontFamily.Default), content = content) }
}
