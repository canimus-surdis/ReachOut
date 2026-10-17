package com.canimussurdis.reachout.ui.wheel

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canimussurdis.reachout.data.SlotState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One wedge on the wheel. */
data class WheelSegment(val label: String, val state: SlotState)

/** Soft, greyed-down pastels so the wheel stays calm and the names stay the loudest thing. */
private val LightSegments = listOf(
    Color(0xFFE3EAE2), Color(0xFFEEE3DF), Color(0xFFECE7D9), // sage, blush, sand
    Color(0xFFE0E6EC), Color(0xFFE6E2EC), Color(0xFFEDE5DB), // mist, lilac, oat
)
private val DarkSegments = listOf(
    Color(0xFF2A322B), Color(0xFF352C2B), Color(0xFF33302A),
    Color(0xFF283039), Color(0xFF2F2C36), Color(0xFF352F29),
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

/**
 * The round wheel, tilted back like a prize wheel lying on a table: a thick rim, pegs, a little
 * light across the top, a soft shadow on the floor, and an upright pointer at the TOP (far) edge.
 *
 * The face is the normal wheel ([drawWheel]) squashed by the tilt, so the same [rotation],
 * [segmentAtPointer] and [rotationToLandOn] work unchanged: the pick is the slice at the top.
 *
 * [tiltDeg] is how far it leans back; [lift] (px) raises it toward you while it spins; [kick] (degrees)
 * flicks the pointer as pegs go past.
 */
fun DrawScope.drawTiltedWheel(
    segments: List<WheelSegment>,
    rotation: Float,
    dark: Boolean,
    pictureSeed: Long,
    photo: ImageBitmap?,
    showWholePicture: Boolean,
    textMeasurer: TextMeasurer,
    textColor: Color,
    coveredColor: Color,
    tiltDeg: Float,
    lift: Float,
    kick: Float,
    hubColor: Color,
    outlineColor: Color,
    pointerColor: Color,
) {
    val squash = cos(radians(tiltDeg)).toFloat()
    val r = size.width / 2f * 0.94f
    val cx = size.width / 2f
    val cy = size.height * 0.47f
    val thickness = 14.dp.toPx()
    val n = segments.size
    val rimTop = if (dark) Color(0xFF3A3A40) else Color(0xFFD6D6D1)
    val rimBottom = if (dark) Color(0xFF141417) else Color(0xFF96968F)
    val floor = Color.Black.copy(alpha = if (dark) 0.6f else 0.3f)

    // Soft shadow on the floor, shrinking as the wheel lifts.
    val floorY = cy + r * squash + thickness + 8.dp.toPx()
    val spread = r * (1f - (lift / (60.dp.toPx())).coerceIn(0f, 0.3f))
    scale(scaleX = 1f, scaleY = 0.26f, pivot = Offset(cx, floorY)) {
        drawCircle(
            Brush.radialGradient(listOf(floor, floor.copy(alpha = 0f)), center = Offset(cx, floorY), radius = spread),
            radius = spread,
            center = Offset(cx, floorY),
        )
    }

    translate(top = -lift) {
        // The rim: the wheel's thickness, darker toward the bottom.
        val steps = 12
        for (k in steps downTo 1) {
            val f = k / steps.toFloat()
            drawOval(
                lerp(rimTop, rimBottom, f),
                topLeft = Offset(cx - r, cy - r * squash + thickness * f),
                size = Size(r * 2f, r * 2f * squash),
            )
        }

        // The face: the normal wheel, tilted back.
        scale(scaleX = 1f, scaleY = squash, pivot = Offset(cx, cy)) {
            inset(left = cx - r, top = cy - r, right = size.width - (cx + r), bottom = size.height - (cy + r)) {
                drawWheel(segments, rotation, dark, pictureSeed, photo, showWholePicture, textMeasurer, textColor, coveredColor)
            }
            val center = Offset(cx, cy)
            if (!showWholePicture && n > 0) {
                // Pegs round the edge, one between each pair of names.
                val sweep = 360f / n
                val pegR = 3.6.dp.toPx()
                val pegColor = if (dark) Color(0xFFC9C9D0) else Color(0xFF8A8A91)
                for (i in 0 until n) {
                    val a = radians(-90f + i * sweep + rotation)
                    val p = Offset(cx + (r - 7.dp.toPx()) * cos(a).toFloat(), cy + (r - 7.dp.toPx()) * sin(a).toFloat())
                    drawCircle(pegColor, pegR, p)
                }
            }
            // Light across the top of the face.
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.12f else 0.5f),
                    0.55f to Color.White.copy(alpha = 0f),
                    1f to Color.Black.copy(alpha = 0.1f),
                    center = Offset(cx - r * 0.3f, cy - r * 0.5f),
                    radius = r * 1.5f,
                ),
                radius = r,
                center = center,
            )
            // The hub.
            val hub = r * 0.15f
            drawCircle(hubColor, hub, center)
            drawCircle(outlineColor, hub, center, style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(pointerColor, hub * 0.18f, center)
        }

        // The pointer stands at the top (far) edge, its tip touching the slice that's picked.
        if (!showWholePicture) {
            val tipY = cy - r * squash + 4.dp.toPx()
            val w = 14.dp.toPx()
            val h = 40.dp.toPx()
            val topY = tipY - h
            val path = Path().apply {
                moveTo(cx - w, topY + 4.dp.toPx())
                quadraticTo(cx, topY, cx + w, topY + 4.dp.toPx())
                lineTo(cx + 1.6.dp.toPx(), tipY - 2.dp.toPx())
                quadraticTo(cx, tipY + 1.dp.toPx(), cx - 1.6.dp.toPx(), tipY - 2.dp.toPx())
                close()
            }
            rotate(kick, Offset(cx, topY + 4.dp.toPx())) {
                translate(top = 3.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.22f)) }
                drawPath(path, pointerColor)
            }
        }
    }
}

/** The name to show: the full name if it fits, otherwise just the first name (cut off with "…" if still too long). */
private fun fitName(textMeasurer: TextMeasurer, label: String, style: TextStyle, maxWidth: Int) =
    textMeasurer.measure(
        text = if (textMeasurer.measure(label, style, maxLines = 1, softWrap = false).size.width <= maxWidth) label
        else label.substringBefore(' '),
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth),
    )

/** An angle in degrees, brought into -180..180. */
private fun normDeg(a: Float): Float = ((a + 180f) % 360f + 360f) % 360f - 180f

private fun radians(deg: Float) = Math.toRadians(deg.toDouble())

/**
 * The Big Wheel: a tall drum seen from the front that rolls downward, like the one on The Price is Right.
 * Names run across it, and the band between the two pointers (the middle) is the pick.
 *
 * It uses the same [rotation] as the round wheel, so [segmentAtPointer] and [rotationToLandOn] work
 * unchanged: segment i covers the drum angles rotation + i·sweep to rotation + (i+1)·sweep, angle 0 faces
 * you, and bigger angles are further down. In reveal mode, revealed bands are windows onto the hidden
 * picture, which sits still behind the drum.
 */
fun DrawScope.drawBigWheel(
    segments: List<WheelSegment>,
    rotation: Float,
    spinning: Boolean,
    dark: Boolean,
    pictureSeed: Long,
    photo: ImageBitmap?,
    showWholePicture: Boolean,
    textMeasurer: TextMeasurer,
    textColor: Color,
    coveredColor: Color,
    railColor: Color,
    outlineColor: Color,
    pegColor: Color,
    pointerColor: Color,
    backgroundColor: Color,
) {
    val railW = 26.dp.toPx()
    val gap = 4.dp.toPx()
    val drum = Rect(railW + gap, 0f, size.width - railW - gap, size.height)
    val r = size.height / 2f
    val cy = r
    val n = segments.size
    val sweep = if (n > 0) 360f / n else 360f

    // Side rails: raised, rounded bars that catch the light in the middle.
    val railDark = lerp(railColor, Color.Black, if (dark) 0.2f else 0.1f)
    val railLight = if (dark) lerp(railColor, Color.White, 0.08f) else lerp(railColor, Color.White, 0.6f)
    for (x in listOf(0f, size.width - railW)) {
        for (k in 1..3) { // soft shadow
            drawRoundRect(
                Color.Black.copy(alpha = if (dark) 0.14f else 0.05f),
                Offset(x - k, k * 2f + 2f), Size(railW + 2f * k, size.height - 4f), CornerRadius(railW / 2f + k),
            )
        }
        drawRoundRect(
            Brush.horizontalGradient(listOf(railDark, railLight, railDark), startX = x, endX = x + railW),
            Offset(x, 0f), Size(railW, size.height), CornerRadius(railW / 2f),
        )
        drawRoundRect(
            outlineColor, Offset(x, 0f), Size(railW, size.height), CornerRadius(railW / 2f),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

    // The drum
    clipPath(Path().apply { addRoundRect(RoundRect(drum, CornerRadius(16.dp.toPx()))) }) {
        drawRect(if (dark) Color(0xFF141217) else Color(0xFF3A322C), drum.topLeft, drum.size)
        if (n == 0 || showWholePicture) {
            drawPictureIn(drum, pictureSeed, photo)
        } else {
            val palette = if (dark) DarkSegments else LightSegments
            val style = TextStyle(
                fontSize = when {
                    n <= 4 -> 28.sp
                    n <= 8 -> 24.sp
                    else -> 22.sp
                },
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            val maxTextWidth = (drum.width - 32.dp.toPx()).toInt().coerceAtLeast(1)
            val divider = Color.White.copy(alpha = if (dark) 0.18f else 0.9f)

            segments.forEachIndexed { i, seg ->
                val mid = normDeg(rotation + (i + 0.5f) * sweep)
                // (With one name, its band wraps all the way round and fills the whole front.)
                val a = if (n == 1) -90f else (mid - sweep / 2f).coerceIn(-90f, 90f)
                val b = if (n == 1) 90f else (mid + sweep / 2f).coerceIn(-90f, 90f)
                val top = cy + r * sin(radians(a)).toFloat()
                val bottom = cy + r * sin(radians(b)).toFloat()
                if (bottom - top < 0.5f) return@forEachIndexed // round the back of the drum
                val band = Rect(drum.left, top, drum.right, bottom)
                // 1 when the band faces you, falling to 0 at the top and bottom edges.
                val facing = cos(radians(mid)).toFloat().coerceAtLeast(0f)

                when (seg.state) {
                    SlotState.REVEALED -> clipRect(band.left, band.top, band.right, band.bottom) {
                        drawPictureIn(drum, pictureSeed, photo)
                    }
                    SlotState.COVERED -> drawRect(coveredColor, band.topLeft, band.size)
                    SlotState.ACTIVE -> {
                        val color = palette[if (n > 1 && i == n - 1 && i % palette.size == 0) 1 else i % palette.size]
                        drawRect(color, band.topLeft, band.size)
                        if (facing > 0.2f) {
                            val layout = fitName(textMeasurer, seg.label, style, maxTextWidth)
                            // Squashed as it curves away, like writing on a real drum.
                            scale(scaleX = 1f, scaleY = facing, pivot = band.center) {
                                drawText(
                                    layout,
                                    topLeft = Offset(
                                        band.center.x - layout.size.width / 2f,
                                        band.center.y - layout.size.height / 2f,
                                    ),
                                    alpha = ((facing - 0.2f) / 0.8f).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                }
                // Darker toward the top and bottom, so it reads as round.
                if (seg.state != SlotState.REVEALED) {
                    drawRect(
                        Color.Black.copy(alpha = (1f - facing) * if (dark) 0.6f else 0.45f),
                        band.topLeft,
                        band.size,
                    )
                }
                if (abs(mid - sweep / 2f) < 90f) {
                    drawLine(divider, Offset(drum.left, top), Offset(drum.right, top), strokeWidth = 2.dp.toPx())
                }
            }
            // The drum curves away into shadow at the top and bottom.
            val back = if (dark) Color(0xFF0A0A0C) else Color(0xFF2E2A27)
            val fade = size.height * 0.22f
            drawRect(Brush.verticalGradient(listOf(back.copy(alpha = 0.6f), back.copy(alpha = 0f)), startY = 0f, endY = fade))
            drawRect(
                Brush.verticalGradient(listOf(back.copy(alpha = 0f), back.copy(alpha = 0.6f)), startY = size.height - fade, endY = size.height),
            )
        }
    }

    if (n == 0) return

    // Pegs on the rails, one at each edge between names.
    val peg = 8.dp.toPx()
    for (i in 0 until n) {
        val e = normDeg(rotation + i * sweep)
        if (abs(e) >= 90f) continue
        val y = cy + r * sin(radians(e)).toFloat()
        val h = peg * cos(radians(e)).toFloat()
        for (x in listOf(railW / 2f, size.width - railW / 2f)) {
            drawOval(pegColor, Offset(x - peg / 2f, y - h / 2f), Size(peg, h))
        }
    }

    if (showWholePicture) return

    // The frame around the middle band: whoever stops here is the pick.
    val windowH = 2f * r * sin(radians(minOf(sweep / 2f, 90f))).toFloat()
    val inset = railW - 4.dp.toPx()
    drawRoundRect(
        pointerColor,
        Offset(inset, cy - windowH / 2f - 3.dp.toPx()),
        Size(size.width - inset * 2f, windowH + 6.dp.toPx()),
        CornerRadius(12.dp.toPx()),
        style = Stroke(width = 3.dp.toPx()),
    )

    // Two pointers. Each peg pushes their tips down as it comes past, then they snap back.
    val frac = ((rotation % sweep) + sweep) % sweep / sweep
    val kick = if (spinning) 24f * ((frac - 0.7f) / 0.3f).coerceIn(0f, 1f) else 0f
    val len = 28.dp.toPx()
    val half = 12.dp.toPx()
    val outline = Stroke(width = 2.dp.toPx())
    val left = Path().apply {
        moveTo(0f, cy - half); lineTo(len, cy); lineTo(0f, cy + half); close()
    }
    val right = Path().apply {
        moveTo(size.width, cy - half); lineTo(size.width - len, cy); lineTo(size.width, cy + half); close()
    }
    val leftPivot = Offset(5.dp.toPx(), cy)
    val rightPivot = Offset(size.width - 5.dp.toPx(), cy)
    rotate(kick, leftPivot) {
        translate(top = 3.dp.toPx()) { drawPath(left, Color.Black.copy(alpha = 0.22f)) }
        drawPath(left, pointerColor)
        drawPath(left, backgroundColor, style = outline)
        drawCircle(backgroundColor, 3.dp.toPx(), leftPivot)
    }
    rotate(-kick, rightPivot) {
        translate(top = 3.dp.toPx()) { drawPath(right, Color.Black.copy(alpha = 0.22f)) }
        drawPath(right, pointerColor)
        drawPath(right, backgroundColor, style = outline)
        drawCircle(backgroundColor, 3.dp.toPx(), rightPivot)
    }
}

/** Which segment is under the pointer at the top, for a given rotation. */
fun segmentAtPointer(rotation: Float, count: Int): Int {
    if (count <= 0) return -1
    val sweep = 360f / count
    val angle = ((-rotation) % 360f + 360f) % 360f
    return (angle / sweep).toInt().coerceIn(0, count - 1)
}

/**
 * A rotation (more than [current]) that stops the pointer inside segment [target], with a few full turns first.
 * [centered] stops exactly in the middle of the segment (the Big Wheel, so the name sits in the middle of the
 * frame); otherwise it stops somewhere inside it, which looks more natural on the round wheel.
 */
fun rotationToLandOn(
    target: Int,
    count: Int,
    current: Float,
    turns: Int,
    random: Random = Random.Default,
    centered: Boolean = false,
): Float {
    val sweep = 360f / count
    val inside = if (centered) sweep / 2f else sweep * (0.2f + 0.6f * random.nextFloat())
    val wanted = -(target * sweep + inside)
    val delta = ((wanted - current) % 360f + 360f) % 360f
    return current + turns * 360f + delta
}

// ---------- The hidden picture ----------

/** The hidden picture filling [rect]: your photo, center-cropped to fit, or the week's design. */
private fun DrawScope.drawPictureIn(rect: Rect, seed: Long, photo: ImageBitmap?) {
    if (photo == null) {
        drawGeneratedPicture(seed, rect.center, maxOf(rect.width, rect.height) / 2f, square = true)
        return
    }
    val aspect = rect.width / rect.height
    val wide = photo.width.toFloat() / photo.height > aspect
    val srcW = if (wide) (photo.height * aspect).toInt() else photo.width
    val srcH = if (wide) photo.height else (photo.width / aspect).toInt()
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - srcW) / 2, (photo.height - srcH) / 2),
        srcSize = IntSize(srcW.coerceAtLeast(1), srcH.coerceAtLeast(1)),
        dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
        dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
    )
}

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

/**
 * A bold abstract design, different each week (seeded by when the batch was drawn).
 * [square] fills the whole square around the circle (for the Big Wheel) instead of just the circle.
 */
fun DrawScope.drawGeneratedPicture(seed: Long, c: Offset, radius: Float, square: Boolean = false) {
    val rnd = Random(seed)
    val colors = ArtPalettes[rnd.nextInt(ArtPalettes.size)].shuffled(rnd)
    if (square) {
        drawRect(colors[0], Offset(c.x - radius, c.y - radius), Size(radius * 2f, radius * 2f))
    } else {
        drawCircle(colors[0], radius, c)
    }

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
