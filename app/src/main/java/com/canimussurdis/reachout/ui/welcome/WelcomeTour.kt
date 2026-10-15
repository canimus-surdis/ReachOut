package com.canimussurdis.reachout.ui.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canimussurdis.reachout.ui.common.AccessoryLook
import com.canimussurdis.reachout.ui.common.AnimalAvatar
import com.canimussurdis.reachout.ui.common.animalBackground

private const val PAGE_COUNT = 6

/**
 * The welcome tour: a few short pages explaining the app.
 * Shown automatically the first time the app opens after each install, and from Settings any time.
 *
 * [firstTime] is false when replayed from Settings (then it just says Done).
 * [onFinish] gets true if the person chose to try the app with pretend contacts first.
 */
@Composable
fun WelcomeTour(
    firstTime: Boolean,
    demoOn: Boolean,
    reduceMotion: Boolean,
    onFinish: (tryDemo: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = PAGE_COUNT - 1
    val finish by rememberUpdatedState(onFinish)
    val offerDemo = firstTime && !demoOn

    fun go(to: Int) {
        page = to.coerceIn(0, last)
    }

    // Back steps through the pages. On the first page of the very first tour, Back leaves the app as usual.
    BackHandler(enabled = page > 0 || !firstTime) {
        if (page > 0) {
            page--
        } else {
            finish(false)
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        // Top bar: app name and Skip.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Reach Out",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            if (page < last) {
                TextButton(onClick = { finish(false) }) { Text(if (firstTime) "Skip" else "Close") }
            }
        }

        // The page itself. Swipe left or right to move between pages.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn() togetherWith fadeOut()
                } else if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "tour page",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    val threshold = 60.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -threshold) {
                                go(page + 1)
                            } else if (total > threshold) {
                                go(page - 1)
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    }
                },
        ) { p ->
            TourPageContent(p, reduceMotion, lastBody(firstTime, offerDemo))
        }

        // Page dots.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clearAndSetSemantics { contentDescription = "Page ${page + 1} of $PAGE_COUNT" },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(PAGE_COUNT) { i ->
                val selected = i == page
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        ),
                )
            }
        }

        // Buttons.
        if (page < last) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) {
                    TextButton(onClick = { go(page - 1) }) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { go(page + 1) }) { Text(if (page == 0) "Show me how" else "Next") }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { finish(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            !firstTime -> "Done"
                            offerDemo -> "Use my contacts"
                            else -> "Let's go"
                        }
                    )
                }
                if (offerDemo) {
                    OutlinedButton(onClick = { finish(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Try it with pretend people first", textAlign = TextAlign.Center)
                    }
                }
                TextButton(onClick = { go(page - 1) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Back")
                }
            }
        }
    }
}

private val TITLES = listOf(
    "Welcome to Reach Out",
    "Pick your people",
    "Spin the wheel",
    "Then just say hi",
    "Check in afterwards",
    "You're all set",
)

private val BODIES = listOf(
    "A gentle nudge to keep in touch with the people you care about, a few at a time.\n\n" +
        "Reach Out only reads your contacts. It never changes, adds or deletes anything, " +
        "and everything stays on your phone.",
    "Swipe right to keep someone, or left to pass. After 3 swipes a Continue button appears, " +
        "but you can keep going as long as you like.\n\n" +
        "Changed your mind? You can fix it any time in Settings → Contacts.",
    "Each week (on Sunday, unless you pick another day), up to 12 people from your list go on " +
        "the wheel. Spin it and let it choose who to reach out to.\n\n" +
        "A reminder can nudge you between 5 and 9 pm.",
    "Call, Text and Share open your phone's own apps with your message ready to go.\n\n" +
        "Not a good moment? Snooze to be reminded in an hour, or put them back in the pool. " +
        "The box stays open until you choose, so nobody slips away.",
    "When you come back to the app, it asks whether you connected.\n\n" +
        "Yes gives that person a break (a random 1 week to 6 months) before they can come up again. " +
        "Not yet puts them straight back in the pool.",
)

private fun lastBody(firstTime: Boolean, offerDemo: Boolean) = when {
    offerDemo -> "Start with your own contacts, or try it out with 20 pretend people first. " +
        "You can turn demo mode on or off any time in Settings.\n\n" +
        "You can see this tour again from Settings."
    firstTime -> "Demo mode is on, so you'll be using pretend people. " +
        "You can turn it off any time in Settings.\n\nYou can see this tour again from Settings."
    else -> "You can see this tour again any time from Settings."
}

@Composable
private fun TourPageContent(page: Int, reduceMotion: Boolean, lastBody: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxHeight
        // Pictures shrink on short screens or with big text, and the page scrolls if it still doesn't fit.
        val artSize = minOf(220.dp, available * 0.42f)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = available)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TourArt(page, artSize, reduceMotion)
            }
            Text(
                TITLES[page],
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (page == PAGE_COUNT - 1) lastBody else BODIES[page],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------- Pictures ----------------

@Composable
private fun TourArt(page: Int, size: Dp, reduceMotion: Boolean) {
    when (page) {
        0 -> AnimalBubble(index = 3, accessory = 8, modifier = Modifier.size(size)) // fox in a top hat
        1 -> SwipeArt(Modifier.size(size))
        2 -> WheelArt(reduceMotion, Modifier.size(size))
        3 -> ActionsArt()
        4 -> CheckInArt()
        else -> AnimalBubble(index = 0, accessory = 10, modifier = Modifier.size(size)) // bear in a party hat
    }
}

@Composable
private fun AnimalBubble(index: Int, accessory: Int, modifier: Modifier = Modifier) {
    val look = remember(accessory) { AccessoryLook.random().copy(accessory = accessory) }
    Box(modifier.clip(CircleShape).background(animalBackground(index))) {
        AnimalAvatar(index, look, Modifier.fillMaxSize())
    }
}

/** A tilted contact card with a KEEP stamp, and Pass / Keep hints underneath. */
@Composable
private fun SwipeArt(modifier: Modifier = Modifier) {
    val look = remember { AccessoryLook.random() }
    Box(modifier.clearAndSetSemantics { contentDescription = "A contact card being swiped right to keep" }) {
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .fillMaxHeight(0.8f)
                .aspectRatio(0.75f)
                .rotate(8f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f).background(animalBackground(1))) {
                AnimalAvatar(1, look, Modifier.fillMaxSize())
                Text(
                    "KEEP",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .rotate(-14f)
                        .border(3.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                )
            }
            Text(
                "Sam",
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Text("← Pass", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Keep →", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        }
    }
}

private val LIGHT_SLICES = listOf(
    Color(0xFFDCE8D5), Color(0xFFF6DCD6), Color(0xFFF6E7C1),
    Color(0xFFD8E6F2), Color(0xFFE4DDF2), Color(0xFFF8DFC8),
)
private val DARK_SLICES = listOf(
    Color(0xFF3A4636), Color(0xFF4A3A3C), Color(0xFF4A4230),
    Color(0xFF33404D), Color(0xFF3D3650), Color(0xFF4C3C30),
)

/** A small 12-slice wheel that turns slowly (stays still with Reduce motion). */
@Composable
private fun WheelArt(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val slices = if (dark) DARK_SLICES else LIGHT_SLICES
    val lines = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    val rotation = if (reduceMotion) {
        0f
    } else {
        val spin = rememberInfiniteTransition(label = "tour wheel")
        spin.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "tour wheel angle",
        ).value
    }
    Canvas(modifier.semantics { contentDescription = "A wheel with 12 names on it" }) {
        val r = size.minDimension / 2f * 0.88f
        val c = Offset(size.width / 2f, size.height / 2f + size.minDimension * 0.04f)
        val topLeft = Offset(c.x - r, c.y - r)
        val box = Size(r * 2, r * 2)
        val sweep = 360f / 12
        withTransform({ rotate(degrees = rotation, pivot = c) }) {
            for (i in 0 until 12) {
                drawArc(slices[i % slices.size], i * sweep, sweep, useCenter = true, topLeft = topLeft, size = box)
                drawArc(
                    lines, i * sweep, sweep, useCenter = true, topLeft = topLeft, size = box,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        drawCircle(lines, radius = r * 0.16f, center = c)
        drawCircle(accent, radius = r * 0.16f, center = c, style = Stroke(width = 3.dp.toPx()))
        val pointer = Path().apply {
            moveTo(c.x - r * 0.11f, c.y - r - r * 0.1f)
            lineTo(c.x + r * 0.11f, c.y - r - r * 0.1f)
            lineTo(c.x, c.y - r + r * 0.12f)
            close()
        }
        drawPath(pointer, accent)
    }
}

/** The four choices from the result box. */
@Composable
private fun ActionsArt() {
    Column(
        Modifier.clearAndSetSemantics { contentDescription = "Buttons: Call, Text, Share and Snooze" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("📞", "Call")
            ActionTile("💬", "Text")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("↗️", "Share")
            ActionTile("⏰", "Snooze")
        }
    }
}

@Composable
private fun ActionTile(emoji: String, label: String) {
    Column(
        Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** A look-alike of the "Did you connect?" question. Not clickable. */
@Composable
private fun CheckInArt() {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clearAndSetSemantics { contentDescription = "Example question: Did you connect with Sam? Not yet, or Yes" },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Did you connect with Sam?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End),
            ) {
                Text("Not yet", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text("Yes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
