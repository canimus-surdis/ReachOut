package com.canimussurdis.reachout.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    secondary = Terracotta,
    onSecondary = Color.White,
    tertiary = Sage,
    onTertiary = Color.White,
    background = Oat,
    onBackground = Cocoa,
    surface = Cream,
    onSurface = Cocoa,
    surfaceVariant = Linen,
    onSurfaceVariant = Taupe,
    surfaceContainer = Cream,
    surfaceContainerHigh = Cream,
    surfaceContainerHighest = Color(0xFFEFE7DC),
    outline = Color(0xFFCDBFB1),
    error = Brick,
)

private val DarkColors = darkColorScheme(
    primary = Peach,
    onPrimary = PeachInk,
    secondary = Peach,
    onSecondary = PeachInk,
    tertiary = Mint,
    onTertiary = Color(0xFF12301A),
    background = PlumNight,
    onBackground = Chalk,
    surface = PlumCard,
    onSurface = Chalk,
    surfaceVariant = PlumMist,
    onSurfaceVariant = Mushroom,
    surfaceContainer = PlumCard,
    surfaceContainerHigh = PlumCard,
    surfaceContainerHighest = Color(0xFF34303A),
    outline = Color(0xFF5A5360),
    error = Rose,
)

/**
 * App theme. Light / Dark / Follow phone is chosen in Settings → Display.
 * Dynamic wallpaper colors are off on purpose to keep the look consistent.
 */
@Composable
fun ReachOutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
