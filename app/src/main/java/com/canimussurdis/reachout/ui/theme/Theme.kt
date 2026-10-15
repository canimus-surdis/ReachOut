package com.canimussurdis.reachout.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.canimussurdis.reachout.data.ColorPalette

/** The handful of colors each palette sets; everything else is worked out from these. */
private class Tones(
    val background: Color,
    val surface: Color,
    val variant: Color,
    val highest: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    val tertiary: Color,   // the KEEP stamp and other "yes" moments
    val onTertiary: Color,
    val outline: Color,
    val error: Color,      // the PASS stamp and warnings
)

/** Each palette as (light, dark). All button and text colors meet accessible contrast. */
private val PALETTES: Map<ColorPalette, Pair<Tones, Tones>> = mapOf(
    ColorPalette.TERRACOTTA to Pair(
        // Warm clay and sage (the original)
        Tones(
        background = Color(0xFFF5EFE6), surface = Color(0xFFFFFDF9),
        variant = Color(0xFFEDE4D8), highest = Color(0xFFEFE7DC),
        text = Color(0xFF2B2420), muted = Color(0xFF6E625A),
        primary = Color(0xFFB95C40), onPrimary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF4F8A5B), onTertiary = Color(0xFFFFFFFF),
        outline = Color(0xFFCDBFB1), error = Color(0xFFC0504D),
    ),
        Tones(
        background = Color(0xFF1F1C24), surface = Color(0xFF2A2630),
        variant = Color(0xFF3A3540), highest = Color(0xFF34303A),
        text = Color(0xFFF3EEE8), muted = Color(0xFFC2B8B0),
        primary = Color(0xFFF0A58A), onPrimary = Color(0xFF3A1A10),
        tertiary = Color(0xFF9CCB9F), onTertiary = Color(0xFF12301A),
        outline = Color(0xFF5A5360), error = Color(0xFFF2A29E),
    ),
    ),
    ColorPalette.OCEAN to Pair(
        // Calm teal and sea glass
        Tones(
        background = Color(0xFFEEF3F5), surface = Color(0xFFFBFDFE),
        variant = Color(0xFFE0E9ED), highest = Color(0xFFE6EEF1),
        text = Color(0xFF1E2A30), muted = Color(0xFF5C6B73),
        primary = Color(0xFF2E7584), onPrimary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF3C8A6C), onTertiary = Color(0xFFFFFFFF),
        outline = Color(0xFFC3D0D6), error = Color(0xFFC2504A),
    ),
        Tones(
        background = Color(0xFF141F27), surface = Color(0xFF1C2A34),
        variant = Color(0xFF2A3A45), highest = Color(0xFF24333E),
        text = Color(0xFFE8F1F4), muted = Color(0xFFA9BBC4),
        primary = Color(0xFF7FC6D4), onPrimary = Color(0xFF0B2A31),
        tertiary = Color(0xFF9BD3B5), onTertiary = Color(0xFF0F3122),
        outline = Color(0xFF4A5D69), error = Color(0xFFF2A29E),
    ),
    ),
    ColorPalette.LAVENDER to Pair(
        // Soft purple and mint
        Tones(
        background = Color(0xFFF3F0F7), surface = Color(0xFFFDFCFF),
        variant = Color(0xFFE7E1F0), highest = Color(0xFFECE7F3),
        text = Color(0xFF26212E), muted = Color(0xFF6A6276),
        primary = Color(0xFF7457A3), onPrimary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF3F8566), onTertiary = Color(0xFFFFFFFF),
        outline = Color(0xFFCFC6DC), error = Color(0xFFC0504D),
    ),
        Tones(
        background = Color(0xFF1C1A24), surface = Color(0xFF26232F),
        variant = Color(0xFF35313F), highest = Color(0xFF2F2B38),
        text = Color(0xFFF0ECF6), muted = Color(0xFFBDB4CA),
        primary = Color(0xFFC9B5EE), onPrimary = Color(0xFF2A1A45),
        tertiary = Color(0xFF9CCFAF), onTertiary = Color(0xFF12301F),
        outline = Color(0xFF565063), error = Color(0xFFF2A29E),
    ),
    ),
    ColorPalette.FOREST to Pair(
        // Leafy green and moss
        Tones(
        background = Color(0xFFEFF2EA), surface = Color(0xFFFCFDF9),
        variant = Color(0xFFE2E8DA), highest = Color(0xFFE7ECE0),
        text = Color(0xFF1F2A1F), muted = Color(0xFF5F6B5C),
        primary = Color(0xFF3B7549), onPrimary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF2F7287), onTertiary = Color(0xFFFFFFFF),
        outline = Color(0xFFC6D0BC), error = Color(0xFFB9504A),
    ),
        Tones(
        background = Color(0xFF171D18), surface = Color(0xFF212922),
        variant = Color(0xFF2F3A30), highest = Color(0xFF29322A),
        text = Color(0xFFECF2EA), muted = Color(0xFFB3C0B0),
        primary = Color(0xFFA3D4A9), onPrimary = Color(0xFF10301A),
        tertiary = Color(0xFF8FC9DA), onTertiary = Color(0xFF0C2C36),
        outline = Color(0xFF4C5A4D), error = Color(0xFFF2A29E),
    ),
    ),
    ColorPalette.SUNSHINE to Pair(
        // Golden honey and leaf
        Tones(
        background = Color(0xFFFBF5E6), surface = Color(0xFFFFFDF6),
        variant = Color(0xFFF1E8CF), highest = Color(0xFFF5EDD8),
        text = Color(0xFF2C2618), muted = Color(0xFF6F6650),
        primary = Color(0xFF9C5A06), onPrimary = Color(0xFFFFFFFF),
        tertiary = Color(0xFF4C8558), onTertiary = Color(0xFFFFFFFF),
        outline = Color(0xFFDCCFAE), error = Color(0xFFC0504D),
    ),
        Tones(
        background = Color(0xFF1E1B14), surface = Color(0xFF29251B),
        variant = Color(0xFF38322A), highest = Color(0xFF322D22),
        text = Color(0xFFF6F0E2), muted = Color(0xFFC9BFA8),
        primary = Color(0xFFF5C46B), onPrimary = Color(0xFF3A2700),
        tertiary = Color(0xFF9CCB9F), onTertiary = Color(0xFF12301A),
        outline = Color(0xFF5B5340), error = Color(0xFFF2A29E),
    ),
    ),
)

private fun Tones.scheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        secondary = primary,
        onSecondary = onPrimary,
        tertiary = tertiary,
        onTertiary = onTertiary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = variant,
        onSurfaceVariant = muted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = highest,
        outline = outline,
        outlineVariant = outline,
        error = error,
    )
}

private fun tonesFor(palette: ColorPalette, dark: Boolean): Tones {
    val pair = PALETTES[palette] ?: PALETTES.getValue(ColorPalette.TERRACOTTA)
    return if (dark) pair.second else pair.first
}

/** Three sample colors (background, main, accent) for the palette picker in Settings. */
fun paletteSwatches(palette: ColorPalette, dark: Boolean): List<Color> =
    tonesFor(palette, dark).let { listOf(it.background, it.primary, it.tertiary) }

/**
 * App theme. Light / Dark / Follow phone and the color palette are chosen in Settings → Display.
 * Dynamic wallpaper colors are off on purpose to keep the look consistent.
 */
@Composable
fun ReachOutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ColorPalette = ColorPalette.TERRACOTTA,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = tonesFor(palette, darkTheme).scheme(darkTheme),
        typography = Typography,
        content = content,
    )
}
