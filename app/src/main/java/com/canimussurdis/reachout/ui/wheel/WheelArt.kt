package com.canimussurdis.reachout.ui.wheel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.canimussurdis.reachout.data.SlotState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One wedge on the wheel. */
data class WheelSegment(val label: String, val state: SlotState)

/** Muted alternating segment colors so the names stay the loudest thing. */
private val LightSegments = listOf(
    Color(0xFFDCE8D5), Color(0xFFF6DCD6), Color(0xFFF6E7C1), // sage, blush, butter
    Color(0xFFD8E6F2), Color(0xFFE4DDF2), Color(0xFFF8DFC8), // sky, lavender, peach
)
private val DarkSegments = listOf(
    Color(0xFF3A4636), Color(0xFF4A3A3C), Color(0xFF4A4230),
    Color(0xFF33404D), Color(0xFF3D3650), Color(0xFF4C3C30),
)

/**
 * Draws the wheel. Angles: segment 0 starts at the top and segments run clockwise;
 * [rotation] turns the whole wheel clockwise, in degrees.
 * In reveal mode, revealed segments show their slice of the hidden picture.
 */
fun DrawScope.drawWheel(
    segments: List<WheelSegment>,
    rotation: Float,
    dark: Boolean,
    pictureSeed: Long,
    /** Your own photo for the hidden picture, or null for a generated design. */
    photo: ImageBitmap?,
    showWholePicture: Boolean,
    textMeasurer: TextMeasurer,
    textColor: Color,
    coveredColor: Color,
) {
    val c = center
    val radius = size.minDimension / 2f
    val box = Rect(c.x - radius, c.y - radius, c.x + radius, c.y + radius)
    val n = segments.size

    if (n == 0 || showWholePicture) {
        clipPath(Path().apply { addOval(box) }) { drawHiddenPicture(pictureSeed, photo, c, radius) }
        drawCircle(Color.Black.copy(alpha = 0.08f), radius, c, style = Stroke(width = radius * 0.02f))
        return
    }

    val sweep = 360f / n
    val palette = if (dark) DarkSegments else LightSegments
    val fontSize = when {
        n <= 4 -> 22.sp
        n <= 8 -> 18.sp
        else -> 15.sp
    }

    segments.forEachIndexed { i, seg ->
        val start = -90f + i * sweep + rotation
        when (seg.state) {
            SlotState.REVEALED -> {
                val wedge = Path().apply {
                    moveTo(c.x, c.y)
                    arcTo(box, start, sweep, forceMoveTo = false)
                    close()
                }
                clipPath(wedge) { drawHiddenPicture(pictureSeed, photo, c, radius) }
            }
            SlotState.COVERED -> drawArc(coveredColor, start, sweep, useCenter = true, topLeft = box.topLeft, size = box.size)
            SlotState.ACTIVE -> {
                // Keep neighbours different even with an odd count.
                val color = palette[if (n > 1 && i == n - 1 && i % palette.size == 0) 1 else i % palette.size]
                drawArc(color, start, sweep, useCenter = true, topLeft = box.topLeft, size = box.size)
                val maxWidth = (radius * 0.7f).toInt().coerceAtLeast(1)
                val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = textColor)
                // Full name if it fits, otherwise just the first name.
                val fullWidth = textMeasurer.measure(seg.label, style, maxLines = 1, softWrap = false).size.width
                val text = if (fullWidth <= maxWidth) seg.label else seg.label.substringBefore(' ')
                val layout = textMeasurer.measure(
                    text = text,
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxWidth),
                )
                val mid = start + sweep / 2f
                val pointsLeft = ((mid % 360f) + 360f) % 360f in 90f..270f
                val y = c.y - layout.size.height / 2f
                if (!pointsLeft) {
                    // Right half: text ends near the rim.
                    rotate(mid, c) { drawText(layout, topLeft = Offset(c.x + radius * 0.9f - layout.size.width, y)) }
                } else {
                    // Left half: turned the other way so it never reads upside down.
                    rotate(mid + 180f, c) { drawText(layout, topLeft = Offset(c.x - radius * 0.9f, y)) }
                }
            }
        }
    }

    // Dividers between segments
    val divider = Color.White.copy(alpha = if (dark) 0.18f else 0.9f)
    for (i in 0 until n) {
        val a = Math.toRadians((-90f + i * sweep + rotation).toDouble())
        drawLine(
            divider,
            c,
            Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat()),
            strokeWidth = radius * 0.012f,
        )
    }
    drawCircle(Color.Black.copy(alpha = 0.08f), radius, c, style = Stroke(width = radius * 0.02f))
}

/** Which segment is under the pointer at the top, for a given rotation. */
fun segmentAtPointer(rotation: Float, count: Int): Int {
    if (count <= 0) return -1
    val sweep = 360f / count
    val angle = ((-rotation) % 360f + 360f) % 360f
    return (angle / sweep).toInt().coerceIn(0, count - 1)
}

/** A rotation (more than [current]) that stops the pointer inside segment [target], with a few full turns first. */
fun rotationToLandOn(target: Int, count: Int, current: Float, turns: Int, random: Random = Random.Default): Float {
    val sweep = 360f / count
    val inside = sweep * (0.2f + 0.6f * random.nextFloat())
    val wanted = -(target * sweep + inside)
    val delta = ((wanted - current) % 360f + 360f) % 360f
    return current + turns * 360f + delta
}

// ---------- The hidden picture ----------

/** Your photo (center-cropped to fill the wheel) or a generated design. */
private fun DrawScope.drawHiddenPicture(seed: Long, photo: ImageBitmap?, c: Offset, radius: Float) {
    if (photo == null) {
        drawGeneratedPicture(seed, c, radius)
        return
    }
    val side = minOf(photo.width, photo.height)
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - side) / 2, (photo.height - side) / 2),
        srcSize = IntSize(side, side),
        dstOffset = IntOffset((c.x - radius).toInt(), (c.y - radius).toInt()),
        dstSize = IntSize((radius * 2).toInt(), (radius * 2).toInt()),
    )
}

private val ArtPalettes = listOf(
    listOf(Color(0xFF264653), Color(0xFF2A9D8F), Color(0xFFE9C46A), Color(0xFFF4A261), Color(0xFFE76F51)),
    listOf(Color(0xFF3D405B), Color(0xFF81B29A), Color(0xFFF2CC8F), Color(0xFFE07A5F), Color(0xFFF4F1DE)),
    listOf(Color(0xFF22223B), Color(0xFF4A4E69), Color(0xFF9A8C98), Color(0xFFC9ADA7), Color(0xFFF2E9E4)),
    listOf(Color(0xFF0B3954), Color(0xFF087E8B), Color(0xFFBFD7EA), Color(0xFFFF5A5F), Color(0xFFC81D25)),
    listOf(Color(0xFF5F0F40), Color(0xFF9A031E), Color(0xFFFB8B24), Color(0xFFE36414), Color(0xFF0F4C5C)),
    listOf(Color(0xFF1B4332), Color(0xFF40916C), Color(0xFF95D5B2), Color(0xFFFFD166), Color(0xFFEF476F)),
)

/** A bold abstract design, different each week (seeded by when the batch was drawn). */
fun DrawScope.drawGeneratedPicture(seed: Long, c: Offset, radius: Float) {
    val rnd = Random(seed)
    val colors = ArtPalettes[rnd.nextInt(ArtPalettes.size)].shuffled(rnd)
    drawCircle(colors[0], radius, c)

    // Big soft rings
    repeat(3) { k ->
        drawCircle(
            colors[1 + k % 4].copy(alpha = 0.85f),
            radius * (0.9f - k * 0.22f),
            Offset(c.x + radius * (rnd.nextFloat() - 0.5f) * 0.6f, c.y + radius * (rnd.nextFloat() - 0.5f) * 0.6f),
            style = Stroke(width = radius * (0.06f + rnd.nextFloat() * 0.08f)),
        )
    }
    // Scattered shapes
    repeat(16) {
        val color = colors[1 + rnd.nextInt(4)]
        val x = c.x + radius * (rnd.nextFloat() * 2f - 1f) * 0.85f
        val y = c.y + radius * (rnd.nextFloat() * 2f - 1f) * 0.85f
        val s = radius * (0.08f + rnd.nextFloat() * 0.22f)
        when (rnd.nextInt(4)) {
            0 -> drawCircle(color, s, Offset(x, y))
            1 -> rotate(rnd.nextFloat() * 90f, Offset(x, y)) {
                drawRect(color, Offset(x - s, y - s), Size(s * 2f, s * 2f))
            }
            2 -> drawPath(
                Path().apply {
                    val a = rnd.nextFloat() * 360f
                    for (p in 0 until 3) {
                        val ang = Math.toRadians((a + p * 120f).toDouble())
                        val px = x + s * 1.3f * cos(ang).toFloat()
                        val py = y + s * 1.3f * sin(ang).toFloat()
                        if (p == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                },
                color,
            )
            else -> rotate(rnd.nextFloat() * 180f, Offset(x, y)) {
                for (line in -2..2) {
                    drawLine(color, Offset(x - s * 1.6f, y + line * s * 0.35f), Offset(x + s * 1.6f, y + line * s * 0.35f), s * 0.14f)
                }
            }
        }
    }
}
