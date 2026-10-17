package com.canimussurdis.reachout.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CONFETTI_COLORS = listOf(
    Color(0xFFB95C40), // terracotta
    Color(0xFF4F8A5B), // sage
    Color(0xFFF2C14E), // butter
    Color(0xFF6FA8DC), // sky
    Color(0xFFE89AA0), // blush
    Color(0xFFA38BD4), // lavender
    Color(0xFFF0A58A), // peach
)

private const val DURATION_S = 3.0f
private const val STILL_DURATION_S = 1.6f

private class Piece(
    val angle: Float,   // launch direction, radians
    val speed: Float,   // screen heights per second
    val spin: Float,    // degrees per second
    val rotation: Float,
    val width: Float,   // fraction of the screen width
    val aspect: Float,
    val round: Boolean,
    val color: Color,
    val stillX: Float,  // reduce motion: where it sits
    val stillY: Float,
) {
    companion object {
        fun random(r: Random) = Piece(
            // Mostly upward, fanning out to both sides.
            angle = ((-90f + (r.nextFloat() - 0.5f) * 140f) * PI / 180f).toFloat(),
            speed = 0.4f + r.nextFloat() * 0.7f,
            spin = (r.nextFloat() - 0.5f) * 900f,
            rotation = r.nextFloat() * 360f,
            width = 0.014f + r.nextFloat() * 0.014f,
            aspect = 0.45f + r.nextFloat() * 0.5f,
            round = r.nextFloat() < 0.25f,
            color = CONFETTI_COLORS[r.nextInt(CONFETTI_COLORS.size)],
            stillX = r.nextFloat(),
            stillY = 0.1f + r.nextFloat() * 0.6f,
        )
    }
}

/**
 * A burst of confetti from the middle of the wheel. Bump [trigger] to fire it again.
 * Doesn't block touches. With [reduceMotion], the pieces stay still and gently fade instead.
 */
@Composable
fun Confetti(trigger: Int, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    // Each burst keeps the Reduce motion setting it started with, so changing the setting
    // afterwards can't bring back a finished burst.
    val still = remember(trigger) { reduceMotion }
    val total = if (still) STILL_DURATION_S else DURATION_S
    val pieces = remember(trigger) {
        val r = Random(trigger * 7919L + System.nanoTime())
        List(if (still) 70 else 160) { Piece.random(r) }
    }
    val clock = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        clock.snapTo(0f)
        clock.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing))
    }
    val t = clock.value
    if (t >= total) return

    Canvas(modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val origin = Offset(w / 2f, h * 0.42f) // about the middle of the wheel
        val gravity = 0.35f // screen heights per second²
        // Fade out over the last 40% of the time.
        val alpha = ((total - t) / (total * 0.4f)).coerceIn(0f, 1f)

        pieces.forEach { p ->
            val pw = p.width * w
            val ph = pw * p.aspect
            val center: Offset
            val rotation: Float
            if (still) {
                center = Offset(p.stillX * w, p.stillY * h)
                rotation = p.rotation
            } else {
                // Air drag slows the burst; gravity pulls everything down.
                val drag = 1f - kotlin.math.exp(-1.8f * t)
                val travel = p.speed / 1.8f * drag
                val x = origin.x + cos(p.angle) * travel * h + sin(t * 3f + p.rotation) * pw * 1.5f
                val y = origin.y + sin(p.angle) * travel * h + 0.5f * gravity * t * t * h
                center = Offset(x, y)
                rotation = p.rotation + p.spin * t
            }
            withTransform({ rotate(rotation, center) }) {
                val topLeft = Offset(center.x - pw / 2f, center.y - ph / 2f)
                if (p.round) {
                    drawOval(p.color, topLeft, Size(pw, pw), alpha = alpha)
                } else {
                    drawRect(p.color, topLeft, Size(pw, ph), alpha = alpha)
                }
            }
        }
    }
}
