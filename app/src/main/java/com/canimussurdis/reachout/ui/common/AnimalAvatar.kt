package com.canimussurdis.reachout.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.random.Random

/**
 * A randomly chosen accessory and color. Picked fresh each time a card is shown,
 * separately from the animal, so any animal can wear anything.
 */
data class AccessoryLook(val accessory: Int, val color: Int) {
    companion object {
        const val COUNT = 12

        /** A random look. Pass the previous card's accessory in [avoid] so two in a row never match. */
        fun random(avoid: Int? = null): AccessoryLook {
            var a: Int
            do {
                a = Random.nextInt(COUNT)
            } while (a == avoid)
            return AccessoryLook(a, Random.nextInt(ACCENTS.size))
        }
    }
}

/**
 * Simple, original cartoon animal faces drawn in code (no image files, no licensing issues).
 * The animal is fixed per contact; the accessory comes from [look].
 */
@Composable
fun AnimalAvatar(index: Int, look: AccessoryLook, modifier: Modifier = Modifier) {
    val species = Math.floorMod(index, 10)
    val description = "Cartoon ${SPECIES_NAMES[species]} ${ACCESSORY_NAMES[look.accessory]}"
    Canvas(modifier.semantics { contentDescription = description }) {
        val s = size.minDimension
        val cx = size.width / 2f
        // Head sits a little low to leave room for tall hats and long ears.
        val cy = size.height / 2f + s * 0.1f
        val r = s * 0.26f
        when (species) {
            0 -> bear(cx, cy, r)
            1 -> cat(cx, cy, r)
            2 -> rabbit(cx, cy, r)
            3 -> fox(cx, cy, r)
            4 -> mouse(cx, cy, r)
            5 -> panda(cx, cy, r)
            6 -> frog(cx, cy, r)
            7 -> dog(cx, cy, r)
            8 -> owl(cx, cy, r)
            else -> pig(cx, cy, r)
        }
        accessory(species, look, cx, cy, r)
    }
}

/** Soft background tint behind the animal. */
fun animalBackground(index: Int): Color = PASTELS[Math.floorMod(index * 7 + index / 10, PASTELS.size)]

private val SPECIES_NAMES = listOf("bear", "cat", "rabbit", "fox", "mouse", "panda", "frog", "dog", "owl", "pig")

private val ACCESSORY_NAMES = listOf(
    "wearing earrings", "wearing a scarf", "wearing a bow tie", "wearing glasses",
    "in a trilby", "in a tricorn hat", "in a sombrero", "in a fez",
    "in a top hat", "in a beanie", "in a party hat", "in a crown",
)

private val PASTELS = listOf(
    Color(0xFFDCEBEA), // mint
    Color(0xFFF3E3D3), // peach
    Color(0xFFE3E0F2), // lavender
    Color(0xFFDDE8F5), // sky
    Color(0xFFF2E8C9), // butter
    Color(0xFFE9E4DE), // stone
)

/** Accessory colors. */
private val ACCENTS = listOf(
    Color(0xFFB23A48), // berry
    Color(0xFFE0A526), // mustard
    Color(0xFF2E4A7D), // navy
    Color(0xFF3F7D4E), // forest
    Color(0xFF6B4C9A), // plum
    Color(0xFFE7735A), // coral
    Color(0xFF2F8F8A), // teal
)

private val Ink = Color(0xFF2B2522)
private val Pink = Color(0xFFF2A7B5)
private val Cream = Color(0xFFF6E9D7)
private val Gold = Color(0xFFE8B931)
private val GoldDark = Color(0xFFB8891A)
private val HatBlack = Color(0xFF2D2A33)
private val Straw = Color(0xFFE2C27A)
private val FezRed = Color(0xFFB8322E)

// ---------- Accessories ----------

private fun DrawScope.accessory(species: Int, look: AccessoryLook, cx: Float, cy: Float, r: Float) {
    val color = ACCENTS[look.color]
    // Where hats sit, and how big, for each animal.
    val isFrog = species == 6 // frog's eyes sit on top, so its hats perch higher and smaller
    var hatY = if (isFrog) cy - r * 1.1f else cy - r * 0.72f
    if (look.accessory == 9 && !isFrog) hatY += r * 0.18f // beanies pull down snug
    val u = if (isFrog) r * 0.95f else r * 1.25f
    when (look.accessory) {
        0 -> earrings(species, cx, cy, r, color)
        1 -> scarf(cx, cy, r, color)
        2 -> bowTie(cx, cy, r, color)
        3 -> glasses(species, cx, cy, r)
        4 -> trilby(cx, hatY, u, color)
        5 -> tricorn(cx, hatY, u, color)
        6 -> sombrero(cx, hatY, u, color)
        7 -> fez(cx, hatY, u)
        8 -> topHat(cx, hatY, u, color)
        9 -> beanie(cx, hatY, u, color)
        10 -> partyHat(cx, hatY, u, color)
        else -> crown(cx, hatY, u, color)
    }
}

private fun DrawScope.earrings(species: Int, cx: Float, cy: Float, r: Float, gem: Color) {
    val (dx, dy) = when (species) {
        7 -> 0.98f to 0.5f // dog: at the bottom of the floppy ears
        6 -> 1.02f to 0.1f // frog: wide head
        else -> 0.93f to 0.2f
    }
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * r * dx
        val y = cy + r * dy
        drawCircle(Gold, r * 0.05f, Offset(x, y))
        drawCircle(Gold, r * 0.16f, Offset(x, y + r * 0.18f), style = Stroke(width = r * 0.05f))
        drawCircle(gem, r * 0.075f, Offset(x, y + r * 0.34f))
    }
}

private fun DrawScope.scarf(cx: Float, cy: Float, r: Float, color: Color) {
    val stripe = Color.White.copy(alpha = 0.55f)
    // Hanging tail
    rotate(-8f, Offset(cx + r * 0.42f, cy + r * 0.95f)) {
        drawRoundRect(color, Offset(cx + r * 0.28f, cy + r * 0.95f), Size(r * 0.28f, r * 0.6f), CornerRadius(r * 0.06f))
        drawRect(stripe, Offset(cx + r * 0.28f, cy + r * 1.3f), Size(r * 0.28f, r * 0.06f))
        drawRect(stripe, Offset(cx + r * 0.28f, cy + r * 1.42f), Size(r * 0.28f, r * 0.06f))
    }
    // Wrap around the neck
    drawRoundRect(color, Offset(cx - r * 0.78f, cy + r * 0.78f), Size(r * 1.56f, r * 0.34f), CornerRadius(r * 0.17f))
    drawRect(stripe, Offset(cx - r * 0.3f, cy + r * 0.78f), Size(r * 0.07f, r * 0.34f))
    drawRect(stripe, Offset(cx + r * 0.2f, cy + r * 0.78f), Size(r * 0.07f, r * 0.34f))
}

private fun DrawScope.bowTie(cx: Float, cy: Float, r: Float, color: Color) {
    val y = cy + r * 1.02f
    tri(color, Offset(cx, y), Offset(cx - r * 0.42f, y - r * 0.22f), Offset(cx - r * 0.42f, y + r * 0.22f))
    tri(color, Offset(cx, y), Offset(cx + r * 0.42f, y - r * 0.22f), Offset(cx + r * 0.42f, y + r * 0.22f))
    drawCircle(color, r * 0.1f, Offset(cx, y))
    drawCircle(Color.Black.copy(alpha = 0.15f), r * 0.1f, Offset(cx, y))
}

private fun DrawScope.glasses(species: Int, cx: Float, cy: Float, r: Float) {
    val (gap, ey, lens) = when (species) {
        8 -> Triple(0.4f, -0.08f, 0.27f) // owl: big eyes
        6 -> Triple(0.5f, -0.72f, 0.3f) // frog: eyes on top
        5 -> Triple(0.38f, -0.05f, 0.28f) // panda: around the patches
        else -> Triple(0.38f, -0.1f, 0.21f)
    }
    val w = r * 0.05f
    val y = cy + r * ey
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * r * gap
        drawCircle(Color.White.copy(alpha = 0.25f), r * lens, Offset(x, y))
        drawCircle(HatBlack, r * lens, Offset(x, y), style = Stroke(width = w))
    }
    drawLine(HatBlack, Offset(cx - r * (gap - lens), y), Offset(cx + r * (gap - lens), y), w, StrokeCap.Round)
}

private fun DrawScope.trilby(hx: Float, hy: Float, u: Float, color: Color) {
    oval(color, hx, hy, u * 1.35f, u * 0.24f)
    val crown = Path().apply {
        moveTo(hx - u * 0.44f, hy)
        lineTo(hx - u * 0.36f, hy - u * 0.52f)
        quadraticTo(hx, hy - u * 0.36f, hx + u * 0.36f, hy - u * 0.52f)
        lineTo(hx + u * 0.44f, hy)
        close()
    }
    drawPath(crown, color)
    drawPath(crown, Color.Black.copy(alpha = 0.1f))
    drawRect(HatBlack, Offset(hx - u * 0.42f, hy - u * 0.16f), Size(u * 0.84f, u * 0.1f))
}

private fun DrawScope.tricorn(hx: Float, hy: Float, u: Float, cockade: Color) {
    oval(HatBlack, hx, hy - u * 0.28f, u * 0.9f, u * 0.6f)
    val brim = Path().apply {
        moveTo(hx - u * 0.98f, hy - u * 0.45f)
        quadraticTo(hx - u * 0.5f, hy - u * 0.02f, hx, hy + u * 0.14f)
        quadraticTo(hx + u * 0.5f, hy - u * 0.02f, hx + u * 0.98f, hy - u * 0.45f)
        quadraticTo(hx + u * 0.5f, hy - u * 0.2f, hx, hy - u * 0.16f)
        quadraticTo(hx - u * 0.5f, hy - u * 0.2f, hx - u * 0.98f, hy - u * 0.45f)
        close()
    }
    drawPath(brim, HatBlack)
    drawPath(brim, Gold, style = Stroke(width = u * 0.05f))
    drawCircle(cockade, u * 0.11f, Offset(hx - u * 0.45f, hy - u * 0.2f))
}

private fun DrawScope.sombrero(hx: Float, hy: Float, u: Float, band: Color) {
    oval(Straw, hx, hy - u * 0.38f, u * 0.78f, u * 0.85f)
    drawRect(band, Offset(hx - u * 0.38f, hy - u * 0.24f), Size(u * 0.76f, u * 0.12f))
    oval(Straw, hx, hy, u * 2.3f, u * 0.42f)
    drawOval(Color(0xFFC9A55E), Offset(hx - u * 1.15f, hy - u * 0.21f), Size(u * 2.3f, u * 0.42f), style = Stroke(width = u * 0.04f))
    for (k in -3..3) drawCircle(band, u * 0.045f, Offset(hx + k * u * 0.3f, hy + u * 0.09f))
}

private fun DrawScope.fez(hx: Float, hy: Float, u: Float) {
    val body = Path().apply {
        moveTo(hx - u * 0.34f, hy)
        lineTo(hx - u * 0.25f, hy - u * 0.62f)
        lineTo(hx + u * 0.25f, hy - u * 0.62f)
        lineTo(hx + u * 0.34f, hy)
        close()
    }
    drawPath(body, FezRed)
    oval(Color(0xFF8E2421), hx, hy - u * 0.62f, u * 0.5f, u * 0.1f)
    val cord = Path().apply {
        moveTo(hx, hy - u * 0.62f)
        quadraticTo(hx + u * 0.42f, hy - u * 0.62f, hx + u * 0.4f, hy - u * 0.28f)
    }
    drawPath(cord, Ink, style = Stroke(width = u * 0.035f, cap = StrokeCap.Round))
    oval(Ink, hx + u * 0.4f, hy - u * 0.2f, u * 0.1f, u * 0.2f)
}

private fun DrawScope.topHat(hx: Float, hy: Float, u: Float, band: Color) {
    oval(HatBlack, hx, hy, u * 1.2f, u * 0.22f)
    drawRect(HatBlack, Offset(hx - u * 0.36f, hy - u * 0.95f), Size(u * 0.72f, u * 0.95f))
    oval(Color(0xFF3B3842), hx, hy - u * 0.95f, u * 0.72f, u * 0.14f)
    drawRect(band, Offset(hx - u * 0.36f, hy - u * 0.24f), Size(u * 0.72f, u * 0.14f))
}

private fun DrawScope.beanie(hx: Float, hy: Float, u: Float, color: Color) {
    drawArc(color, 180f, 180f, useCenter = true, topLeft = Offset(hx - u * 0.64f, hy - u * 0.56f), size = Size(u * 1.28f, u * 1.12f))
    drawCircle(Cream, u * 0.16f, Offset(hx, hy - u * 0.62f))
    drawRoundRect(color, Offset(hx - u * 0.68f, hy - u * 0.12f), Size(u * 1.36f, u * 0.24f), CornerRadius(u * 0.1f))
    drawRoundRect(Color.Black.copy(alpha = 0.15f), Offset(hx - u * 0.68f, hy - u * 0.12f), Size(u * 1.36f, u * 0.24f), CornerRadius(u * 0.1f))
}

private fun DrawScope.partyHat(hx: Float, hy: Float, u: Float, color: Color) {
    val tip = Offset(hx + u * 0.08f, hy - u * 0.98f)
    tri(color, Offset(hx - u * 0.34f, hy), Offset(hx + u * 0.34f, hy), tip)
    for ((dx, dy) in listOf(-0.12f to -0.2f, 0.14f to -0.32f, -0.02f to -0.55f, 0.1f to -0.74f)) {
        drawCircle(Color.White.copy(alpha = 0.8f), u * 0.045f, Offset(hx + u * dx, hy + u * dy))
    }
    drawCircle(Gold, u * 0.1f, tip)
}

private fun DrawScope.crown(hx: Float, hy: Float, u: Float, gem: Color) {
    val shape = Path().apply {
        moveTo(hx - u * 0.45f, hy)
        lineTo(hx - u * 0.45f, hy - u * 0.55f)
        lineTo(hx - u * 0.22f, hy - u * 0.3f)
        lineTo(hx, hy - u * 0.62f)
        lineTo(hx + u * 0.22f, hy - u * 0.3f)
        lineTo(hx + u * 0.45f, hy - u * 0.55f)
        lineTo(hx + u * 0.45f, hy)
        close()
    }
    drawPath(shape, Gold)
    drawPath(shape, GoldDark, style = Stroke(width = u * 0.04f))
    drawCircle(gem, u * 0.08f, Offset(hx, hy - u * 0.15f))
    for (x in listOf(-0.45f, 0f, 0.45f)) {
        val tipY = if (x == 0f) -0.62f else -0.55f
        drawCircle(Color.White, u * 0.05f, Offset(hx + u * x, hy + u * tipY))
    }
}

// ---------- Animals ----------

private fun DrawScope.bear(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFF9C6B4A)
    val inner = Color(0xFFD9B08C)
    for (side in listOf(-1f, 1f)) {
        drawCircle(fur, r * 0.36f, Offset(cx + side * r * 0.78f, cy - r * 0.78f))
        drawCircle(inner, r * 0.18f, Offset(cx + side * r * 0.78f, cy - r * 0.78f))
    }
    drawCircle(fur, r, Offset(cx, cy))
    oval(Cream, cx, cy + r * 0.38f, r * 0.95f, r * 0.68f)
    oval(Ink, cx, cy + r * 0.2f, r * 0.32f, r * 0.22f)
    eyes(cx, cy, r)
    smile(cx, cy + r * 0.42f, r * 0.16f, r * 0.05f)
}

private fun DrawScope.cat(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFFE3A55B)
    pointyEars(cx, cy, r, fur, Pink)
    drawCircle(fur, r, Offset(cx, cy))
    eyes(cx, cy, r)
    tri(Pink, Offset(cx - r * 0.1f, cy + r * 0.15f), Offset(cx + r * 0.1f, cy + r * 0.15f), Offset(cx, cy + r * 0.27f))
    smile(cx, cy + r * 0.3f, r * 0.14f, r * 0.045f)
    whiskers(cx, cy, r)
}

private fun DrawScope.rabbit(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFFD9D4CF)
    for (side in listOf(-1f, 1f)) {
        oval(fur, cx + side * r * 0.38f, cy - r * 1.12f, r * 0.42f, r * 1.2f)
        oval(Pink, cx + side * r * 0.38f, cy - r * 1.08f, r * 0.2f, r * 0.9f)
    }
    drawCircle(fur, r, Offset(cx, cy))
    eyes(cx, cy, r)
    oval(Pink, cx, cy + r * 0.2f, r * 0.2f, r * 0.14f)
    smile(cx, cy + r * 0.3f, r * 0.13f, r * 0.045f)
    drawCircle(Pink.copy(alpha = 0.5f), r * 0.13f, Offset(cx - r * 0.58f, cy + r * 0.25f))
    drawCircle(Pink.copy(alpha = 0.5f), r * 0.13f, Offset(cx + r * 0.58f, cy + r * 0.25f))
}

private fun DrawScope.fox(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFFE07B39)
    pointyEars(cx, cy, r, fur, Ink)
    drawCircle(fur, r, Offset(cx, cy))
    oval(Color.White, cx, cy + r * 0.5f, r * 1.3f, r * 0.8f)
    eyes(cx, cy, r)
    oval(Ink, cx, cy + r * 0.3f, r * 0.24f, r * 0.16f)
    smile(cx, cy + r * 0.45f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.mouse(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFFA7A9AC)
    for (side in listOf(-1f, 1f)) {
        drawCircle(fur, r * 0.5f, Offset(cx + side * r * 0.82f, cy - r * 0.7f))
        drawCircle(Pink, r * 0.32f, Offset(cx + side * r * 0.82f, cy - r * 0.7f))
    }
    drawCircle(fur, r, Offset(cx, cy))
    eyes(cx, cy, r)
    drawCircle(Pink, r * 0.1f, Offset(cx, cy + r * 0.22f))
    smile(cx, cy + r * 0.34f, r * 0.13f, r * 0.045f)
    whiskers(cx, cy, r)
}

private fun DrawScope.panda(cx: Float, cy: Float, r: Float) {
    for (side in listOf(-1f, 1f)) drawCircle(Ink, r * 0.32f, Offset(cx + side * r * 0.75f, cy - r * 0.78f))
    drawCircle(Color.White, r, Offset(cx, cy))
    drawCircle(Ink.copy(alpha = 0.12f), r, Offset(cx, cy), style = Stroke(width = r * 0.03f))
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.38f
        val ey = cy - r * 0.05f
        rotate(side * -20f, Offset(ex, ey)) { oval(Ink, ex, ey, r * 0.36f, r * 0.5f) }
        drawCircle(Color.White, r * 0.08f, Offset(ex, ey - r * 0.03f))
        drawCircle(Ink, r * 0.045f, Offset(ex, ey - r * 0.03f))
    }
    oval(Ink, cx, cy + r * 0.28f, r * 0.24f, r * 0.16f)
    smile(cx, cy + r * 0.43f, r * 0.13f, r * 0.045f)
}

private fun DrawScope.frog(cx: Float, cy: Float, r: Float) {
    val skin = Color(0xFF7DB86A)
    for (side in listOf(-1f, 1f)) drawCircle(skin, r * 0.36f, Offset(cx + side * r * 0.5f, cy - r * 0.72f))
    oval(skin, cx, cy + r * 0.05f, r * 2.1f, r * 1.7f)
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.5f
        val ey = cy - r * 0.72f
        drawCircle(Color.White, r * 0.24f, Offset(ex, ey))
        drawCircle(Ink, r * 0.12f, Offset(ex, ey))
        drawCircle(Color.White, r * 0.04f, Offset(ex + r * 0.04f, ey - r * 0.04f))
        drawCircle(Pink.copy(alpha = 0.6f), r * 0.14f, Offset(cx + side * r * 0.62f, cy + r * 0.32f))
        drawCircle(Ink, r * 0.03f, Offset(cx + side * r * 0.1f, cy - r * 0.05f))
    }
    val mouth = Path().apply {
        moveTo(cx - r * 0.45f, cy + r * 0.2f)
        quadraticTo(cx, cy + r * 0.62f, cx + r * 0.45f, cy + r * 0.2f)
    }
    drawPath(mouth, Ink, style = Stroke(width = r * 0.05f, cap = StrokeCap.Round))
}

private fun DrawScope.dog(cx: Float, cy: Float, r: Float) {
    val fur = Color(0xFFC89B6D)
    val earColor = Color(0xFF7A5236)
    drawCircle(fur, r, Offset(cx, cy))
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.88f
        val ey = cy - r * 0.08f
        rotate(side * 22f, Offset(ex, ey - r * 0.4f)) { oval(earColor, ex, ey, r * 0.48f, r * 1.0f) }
    }
    oval(Cream, cx, cy + r * 0.4f, r * 0.9f, r * 0.62f)
    eyes(cx, cy, r)
    oval(Ink, cx, cy + r * 0.22f, r * 0.3f, r * 0.2f)
    smile(cx, cy + r * 0.42f, r * 0.15f, r * 0.05f)
    oval(Pink, cx + r * 0.08f, cy + r * 0.58f, r * 0.14f, r * 0.16f)
}

private fun DrawScope.owl(cx: Float, cy: Float, r: Float) {
    val feathers = Color(0xFF8A6A4F)
    val face = Color(0xFFE8D5BC)
    for (side in listOf(-1f, 1f)) {
        tri(
            feathers,
            Offset(cx + side * r * 0.88f, cy - r * 0.5f),
            Offset(cx + side * r * 0.78f, cy - r * 1.15f),
            Offset(cx + side * r * 0.32f, cy - r * 0.82f),
        )
    }
    drawCircle(feathers, r, Offset(cx, cy))
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.4f
        val ey = cy - r * 0.08f
        drawCircle(face, r * 0.42f, Offset(ex, ey))
        drawCircle(Ink, r * 0.2f, Offset(ex, ey))
        drawCircle(Color.White, r * 0.06f, Offset(ex + r * 0.06f, ey - r * 0.06f))
    }
    tri(Color(0xFFE9A23B), Offset(cx - r * 0.11f, cy + r * 0.22f), Offset(cx + r * 0.11f, cy + r * 0.22f), Offset(cx, cy + r * 0.45f))
}

private fun DrawScope.pig(cx: Float, cy: Float, r: Float) {
    val skin = Color(0xFFF3B0B8)
    val dark = Color(0xFFE48A98)
    for (side in listOf(-1f, 1f)) {
        tri(
            dark,
            Offset(cx + side * r * 0.9f, cy - r * 0.42f),
            Offset(cx + side * r * 0.82f, cy - r * 1.0f),
            Offset(cx + side * r * 0.36f, cy - r * 0.82f),
        )
    }
    drawCircle(skin, r, Offset(cx, cy))
    eyes(cx, cy, r)
    oval(dark, cx, cy + r * 0.3f, r * 0.6f, r * 0.42f)
    oval(Ink, cx - r * 0.11f, cy + r * 0.3f, r * 0.08f, r * 0.13f)
    oval(Ink, cx + r * 0.11f, cy + r * 0.3f, r * 0.08f, r * 0.13f)
    smile(cx, cy + r * 0.62f, r * 0.12f, r * 0.045f)
}

// ---------- Shared parts ----------

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, w: Float, h: Float) =
    drawOval(color, topLeft = Offset(cx - w / 2f, cy - h / 2f), size = Size(w, h))

private fun DrawScope.tri(color: Color, a: Offset, b: Offset, c: Offset) =
    drawPath(
        Path().apply {
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            lineTo(c.x, c.y)
            close()
        },
        color,
    )

private fun DrawScope.eyes(cx: Float, cy: Float, r: Float) {
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.38f
        val ey = cy - r * 0.1f
        drawCircle(Ink, r * 0.09f, Offset(ex, ey))
        drawCircle(Color.White, r * 0.03f, Offset(ex + r * 0.03f, ey - r * 0.03f))
    }
}

private fun DrawScope.smile(cx: Float, y: Float, w: Float, stroke: Float) {
    val p = Path().apply {
        moveTo(cx - w, y)
        quadraticTo(cx - w / 2f, y + w * 0.8f, cx, y)
        quadraticTo(cx + w / 2f, y + w * 0.8f, cx + w, y)
    }
    drawPath(p, Ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

private fun DrawScope.pointyEars(cx: Float, cy: Float, r: Float, outer: Color, inner: Color) {
    for (side in listOf(-1f, 1f)) {
        tri(
            outer,
            Offset(cx + side * r * 0.95f, cy - r * 0.25f),
            Offset(cx + side * r * 0.75f, cy - r * 1.15f),
            Offset(cx + side * r * 0.2f, cy - r * 0.85f),
        )
        tri(
            inner,
            Offset(cx + side * r * 0.78f, cy - r * 0.45f),
            Offset(cx + side * r * 0.7f, cy - r * 0.95f),
            Offset(cx + side * r * 0.38f, cy - r * 0.78f),
        )
    }
}

private fun DrawScope.whiskers(cx: Float, cy: Float, r: Float) {
    val c = Ink.copy(alpha = 0.6f)
    val w = r * 0.025f
    for (side in listOf(-1f, 1f)) {
        drawLine(c, Offset(cx + side * r * 0.3f, cy + r * 0.25f), Offset(cx + side * r * 0.95f, cy + r * 0.12f), w, StrokeCap.Round)
        drawLine(c, Offset(cx + side * r * 0.3f, cy + r * 0.3f), Offset(cx + side * r * 0.95f, cy + r * 0.38f), w, StrokeCap.Round)
    }
}
