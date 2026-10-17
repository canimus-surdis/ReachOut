package com.canimussurdis.reachout.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/*
 * The app's sense of depth: a softly lit background, raised surfaces that cast real shadows and
 * catch a little light along their top edge, and recessed tracks that things sit inside.
 * Every color comes from the current palette, so all palettes (and dark mode) get depth too.
 */

/** Which colors a raised surface uses. */
enum class Tone {
    /** White cards, sheets and round buttons. */
    Surface,

    /** The main action (Spin, Call, Yes): the palette's button color. */
    Ink,

    /** Secondary round buttons: a touch darker than a card. */
    Soft,
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** A softly lit background: brightest near the top middle, a little darker toward the edges. */
@Composable
fun Modifier.depthBackground(): Modifier {
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.5f
    val hi = if (dark) lerp(bg, Color.White, 0.07f) else lerp(bg, Color.White, 0.9f)
    val lo = if (dark) lerp(bg, Color.Black, 0.45f) else lerp(bg, Color.Black, 0.075f)
    return this.drawBehind {
        drawRect(
            Brush.radialGradient(
                0f to hi,
                0.5f to bg,
                1f to lo,
                center = Offset(size.width / 2f, size.height * 0.28f),
                radius = max(size.width, size.height) * 0.85f,
            )
        )
    }
}

/** A raised surface: casts a soft shadow, lit slightly from above. Clips its content to [shape]. */
@Composable
fun Modifier.raised(shape: Shape, elevation: Dp = 8.dp, tone: Tone = Tone.Surface): Modifier {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val top: Color
    val bottom: Color
    when (tone) {
        Tone.Surface -> {
            top = if (dark) lerp(cs.surface, Color.White, 0.04f) else cs.surface
            bottom = if (dark) cs.surface else lerp(cs.surface, cs.background, 0.6f)
        }
        Tone.Ink -> {
            top = if (dark) cs.primary else lerp(cs.primary, Color.White, 0.14f)
            bottom = lerp(cs.primary, Color.Black, if (dark) 0.14f else 0.12f)
        }
        Tone.Soft -> {
            top = if (dark) lerp(cs.surfaceVariant, Color.White, 0.06f) else cs.surface
            bottom = if (dark) cs.surfaceVariant else lerp(cs.surface, cs.surfaceVariant, 0.7f)
        }
    }
    val highlight = Color.White.copy(
        alpha = when {
            dark -> 0.07f
            tone == Tone.Ink -> 0.16f
            else -> 0.9f
        }
    )
    val shade = if (dark) Color.Black else Color(0xFF141418)
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shade.copy(alpha = if (dark) 0.6f else 0.16f),
            spotColor = shade.copy(alpha = if (dark) 0.75f else 0.26f),
        )
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawWithContent {
            drawContent()
            // Light catching the top edge.
            drawLine(highlight, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
        }
}

/** A recessed track, pressed into the surface (for choices that slide between options). */
@Composable
fun Modifier.recessed(shape: Shape): Modifier {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val base = if (dark) lerp(cs.background, Color.Black, 0.35f) else lerp(cs.surfaceVariant, Color.Black, 0.02f)
    val shade = Color.Black.copy(alpha = if (dark) 0.5f else 0.11f)
    return this
        .clip(shape)
        .background(base)
        .drawWithContent {
            // A soft inner shadow along the top edge.
            drawRect(Brush.verticalGradient(0f to shade, 0.3f to Color.Transparent))
            drawContent()
        }
}

/** The main raised button (Spin, Continue, Yes). */
@Composable
fun InkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val onInk = MaterialTheme.colorScheme.onPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = onInk,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = onInk.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .height(height)
            .raised(CircleShape, if (enabled) 10.dp else 3.dp, Tone.Ink),
        content = content,
    )
}

/** A softer raised button for the other choice (Not yet). */
@Composable
fun SoftButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
            .height(height)
            .raised(CircleShape, 6.dp, Tone.Soft),
        content = content,
    )
}
