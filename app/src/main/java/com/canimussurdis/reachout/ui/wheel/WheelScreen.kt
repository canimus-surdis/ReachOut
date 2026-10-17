package com.canimussurdis.reachout.ui.wheel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import com.canimussurdis.reachout.ui.common.raised
import com.canimussurdis.reachout.ui.common.Tone
import com.canimussurdis.reachout.ui.common.InkButton
import kotlin.math.sin
import kotlin.math.abs
import android.Manifest
import com.canimussurdis.reachout.ui.common.phoneLine
import com.canimussurdis.reachout.ui.common.AppIcons
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.canimussurdis.reachout.OpenRequest
import com.canimussurdis.reachout.data.Contact
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.data.ReachedMode
import com.canimussurdis.reachout.data.SlotState
import com.canimussurdis.reachout.data.WheelPhotos
import com.canimussurdis.reachout.data.WheelStyle
import com.canimussurdis.reachout.ui.common.AccessoryLook
import com.canimussurdis.reachout.ui.common.ContactPicture
import com.canimussurdis.reachout.reminders.Reminders
import com.canimussurdis.reachout.ui.common.MenuButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

/**
 * Screen 2: this week's wheel. Spin it, and the chosen person appears in a box
 * with Call, Text, Share and Snooze.
 * [dataVersion] changes whenever data may have changed elsewhere (e.g. after "Did you connect?").
 */
@Composable
fun WheelScreen(
    repo: ReachOutRepository,
    dataVersion: Int,
    /** Goes up each time the app is opened; the picked person's box opens again then. */
    openCount: Int,
    onAddPeople: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    /** From a tapped reminder: open straight to this person's result box. */
    openRequest: OpenRequest? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    var localVersion by remember { mutableIntStateOf(0) }
    var spinning by remember { mutableStateOf(false) }
    val snap = remember(dataVersion, localVersion) { repo.snapshot() }
    val settings = snap.settings

    // Freeze the wheel's contents while it spins so nothing shifts under the pointer.
    val liveSlots = snap.wheel.slots
    // (A plain holder, not Compose state, because it's updated while drawing.)
    val frozen = remember { arrayOf(liveSlots) }
    if (!spinning) frozen[0] = liveSlots
    val slots = frozen[0]

    val rotation = remember { Animatable(0f) }
    val wheelAlpha = remember { Animatable(1f) }
    // The picked person stays picked (saved, even across restarts) until you Call, Text, Share
    // or put them back in the pool. Their box opens whenever the app is opened.
    // Snooze can be used once per spin: it closes the box for now, and the box comes back
    // (with Snooze greyed out) the next time the app is opened or the reminder is tapped.
    var pickSnoozed by remember { mutableStateOf(repo.isPickSnoozed()) }
    var result by remember { mutableStateOf(repo.currentPick()) }
    var resultLook by remember { mutableStateOf(AccessoryLook.random()) }
    var showSnooze by remember { mutableStateOf(false) }

    // Every time the app is opened: show the picked person's box.
    LaunchedEffect(openCount) {
        pickSnoozed = repo.isPickSnoozed()
        if (result == null) {
            repo.currentPick()?.let {
                resultLook = AccessoryLook.random(avoid = resultLook.accessory)
                result = it
            }
        }
    }

    // The pick may have been dealt with outside this screen (for example Call or Text on the
    // home screen widget). If so, close their box when you come back.
    LaunchedEffect(dataVersion) {
        val shown = result
        if (shown != null && repo.currentPick()?.id != shown.id) {
            result = null
            pickSnoozed = false
        }
    }

    LaunchedEffect(openRequest) {
        val id = openRequest?.contactId ?: return@LaunchedEffect
        repo.contactById(id)?.let {
            resultLook = AccessoryLook.random(avoid = resultLook.accessory)
            result = it
            repo.setCurrentPick(it.id)
            pickSnoozed = repo.isPickSnoozed()
        }
    }

    /** Just snoozed (box closed for now): the wheel waits for this person. */
    val waitingFor = if (result == null && pickSnoozed) repo.currentPick() else null

    // Ask once for notification permission, the first time you reach the wheel with reminders on.
    var notifyAfterGrant by remember { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Reminders.rescheduleAll(context)
        val pending = notifyAfterGrant
        notifyAfterGrant = null
        if (pending != null) {
            if (granted) {
                val at = Reminders.snoozeOneHour(context, pending)
                repo.snoozeCurrentPick()
                pickSnoozed = true
                result = null
                Toast.makeText(context, "I'll remind you ${timeLabel(at, repo.now())}", Toast.LENGTH_SHORT).show()
            } else {
                // No notifications means no reminder: keep the box so you can choose something else.
                Toast.makeText(context, "Notifications are off, so I can't remind you. Pick another option.", Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (settings.remindersOn && !settings.askedNotificationPermission && !Reminders.notificationsAllowed(context)) {
            repo.changeSettings { it.copy(askedNotificationPermission = true) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val reveal = settings.reachedMode == ReachedMode.REVEAL

    // This week's hidden picture: one of your photos, or null for a generated design.
    val photoName = if (reveal) WheelPhotos.choose(snap.wheel.drawnAt, settings) else null
    val photo by produceState<ImageBitmap?>(null, photoName) {
        value = photoName?.let { name ->
            withContext(Dispatchers.IO) { WheelPhotos.load(context, name)?.asImageBitmap() }
        }
    }
    val activeIndexes = slots.indices.filter { slots[it].state == SlotState.ACTIVE }
    val everyoneDone = slots.isNotEmpty() && activeIndexes.isEmpty()
    val allRevealed = everyoneDone && slots.all { it.state == SlotState.REVEALED }

    // Soft "tick" as each name passes the pointer.
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 22) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    fun spin() {
        if (spinning || result != null || waitingFor != null || activeIndexes.isEmpty()) return
        spinning = true
        val count = slots.size
        val target = activeIndexes.random()
        scope.launch {
            // Same position, smaller number: keeps the landing exact however many times you spin.
            rotation.snapTo(rotation.value % 360f)
            val final = rotationToLandOn(
                target, count, rotation.value, turns = 5 + Random.nextInt(3),
                centered = settings.wheelStyle == WheelStyle.BIG,
            )
            if (settings.reduceMotion) {
                wheelAlpha.animateTo(0.3f, tween(150))
                rotation.snapTo(final)
                wheelAlpha.animateTo(1f, tween(250))
            } else {
                var lastSegment = segmentAtPointer(rotation.value, count)
                var lastTick = 0L
                rotation.animateTo(
                    final,
                    tween(durationMillis = 4200, easing = CubicBezierEasing(0.12f, 0.75f, 0.12f, 1f)),
                ) {
                    val seg = segmentAtPointer(value, count)
                    val now = System.currentTimeMillis()
                    if (seg != lastSegment && now - lastTick > 45) {
                        lastSegment = seg
                        lastTick = now
                        if (settings.spinSound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 12)
                        if (settings.spinVibration) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
            if (settings.spinVibration) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(150)
            resultLook = AccessoryLook.random(avoid = resultLook.accessory)
            result = slots.getOrNull(target)?.contact
            repo.setCurrentPick(result?.id)
            pickSnoozed = repo.isPickSnoozed() // a new spin always gets its own snooze
            spinning = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "This week",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "New names every ${settings.batchDay.getDisplayName(TextStyle.FULL, Locale.getDefault())}" +
                        if (slots.isNotEmpty()) " · ${slots.size} on the wheel" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MenuButton(onClick = onOpenMenu)
        }

        // Wheel area
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (slots.isEmpty() || (everyoneDone && !reveal)) {
                EmptyWheel(hasPeople = snap.accepted.isNotEmpty(), onAddPeople = onAddPeople)
            } else {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val textColor = MaterialTheme.colorScheme.onSurface
                val covered = MaterialTheme.colorScheme.surfaceVariant
                val pointerColor = MaterialTheme.colorScheme.primary
                val hubColor = MaterialTheme.colorScheme.surface
                val segments = slots.map { WheelSegment(it.contact.name, it.state) }
                // Both styles use the same spin, pick and rules; only the drawing differs.
                val big = settings.wheelStyle == WheelStyle.BIG
                val railColor = MaterialTheme.colorScheme.surface
                val outlineColor = MaterialTheme.colorScheme.outline
                val pegColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                val backgroundColor = MaterialTheme.colorScheme.background

                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (big) Modifier.fillMaxHeight() else Modifier.aspectRatio(1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .padding(if (big) PaddingValues(vertical = 8.dp) else PaddingValues(0.dp))
                            .alpha(wheelAlpha.value * if (result != null) 0.35f else 1f)
                            .semantics {
                                contentDescription = "Wheel with ${activeIndexes.size} names. Double tap to spin."
                            }
                            .pointerInput(slots, spinning) {
                                detectDragGestures(onDragEnd = { spin() }) { change, _ -> change.consume() }
                            }
                            .clickable(enabled = !spinning) { spin() },
                    ) {
                        if (big) {
                            drawBigWheel(
                                segments = segments,
                                rotation = rotation.value,
                                spinning = spinning,
                                dark = dark,
                                pictureSeed = snap.wheel.drawnAt,
                                photo = photo,
                                showWholePicture = reveal && everyoneDone,
                                textMeasurer = textMeasurer,
                                textColor = textColor,
                                coveredColor = covered,
                                railColor = railColor,
                                outlineColor = outlineColor,
                                pegColor = pegColor,
                                pointerColor = pointerColor,
                                backgroundColor = backgroundColor,
                            )
                        } else {
                            // While spinning: lifts toward you, tips forward a little and wobbles.
                            val turnsPerSecond = if (spinning) abs(rotation.velocity) / 360f else 0f
                            val lift = minOf(16.dp.toPx(), turnsPerSecond * 4.dp.toPx())
                            val wobble = sin(rotation.value / 25f) * minOf(2.2f, turnsPerSecond * 0.6f)
                            val sweep = if (segments.isNotEmpty()) 360f / segments.size else 360f
                            val frac = ((rotation.value % sweep + sweep) % sweep) / sweep
                            drawTiltedWheel(
                                segments = segments,
                                rotation = rotation.value,
                                dark = dark,
                                pictureSeed = snap.wheel.drawnAt,
                                photo = photo,
                                showWholePicture = reveal && everyoneDone,
                                textMeasurer = textMeasurer,
                                textColor = textColor,
                                coveredColor = covered,
                                tiltDeg = WHEEL_TILT - minOf(7f, turnsPerSecond * 2f) + wobble,
                                lift = lift,
                                kick = if (spinning) -22f * ((frac - 0.7f) / 0.3f).coerceIn(0f, 1f) else 0f,
                                hubColor = hubColor,
                                outlineColor = outlineColor,
                                pointerColor = pointerColor,
                            )
                        }
                    }
                    if (reveal && everyoneDone) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        ) {
                            Text(
                                if (allRevealed) "You reached everyone this week!" else "That's everyone for this week",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // The chosen person, laid over the wheel.
            // (Full name needed: inside a Box inside a Column, plain AnimatedVisibility is ambiguous.)
            androidx.compose.animation.AnimatedVisibility(
                visible = result != null,
                enter = fadeIn() + slideInVertically { it / 4 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val person = result
                if (person != null) {
                    ResultBox(
                        contact = person,
                        look = resultLook,
                        onCall = {
                            repo.markReachedOut(person.id)
                            pickSnoozed = false
                            openDialer(context, person.mainNumber)
                            result = null
                        },
                        onText = {
                            repo.markReachedOut(person.id)
                            pickSnoozed = false
                            openTexting(context, person.mainNumber, settings.message)
                            result = null
                        },
                        onShare = {
                            repo.markReachedOut(person.id)
                            pickSnoozed = false
                            openShare(context, person.name, settings.message)
                            result = null
                        },
                        onSnooze = { showSnooze = true },
                        canSnooze = !pickSnoozed,
                    )
                }
            }
        }

        // Refill mode with empty spots and nobody left to fill them: say why, and offer a way to fix it.
        val emptySpots = settings.wheelSize - slots.size
        if (!reveal && result == null && slots.isNotEmpty() && emptySpots > 0 && snap.spareForWheel == 0) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onAddPeople() },
            ) {
                Text(
                    "Nobody else is ready to join the wheel, so empty spots stay empty. " +
                        "Tap here to add more people.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Just snoozed: a short note instead of the Spin button. The box opens again next time.
        if (waitingFor != null) {
            val at = settings.snoozes[waitingFor.id]
            Text(
                "${waitingFor.name} is snoozed" +
                    (if (at != null) " until ${timeLabel(at, repo.now()).removePrefix("at ")}" else "") +
                    ". Their box opens again next time you open the app. Spinning is paused until you reach out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        // Bottom controls
        if (slots.isNotEmpty() && activeIndexes.isNotEmpty() && result == null && waitingFor == null) {
            InkButton(
                onClick = { spin() },
                enabled = !spinning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (spinning) "Spinning…" else "Spin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(
            onClick = onAddPeople,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add more people", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
        Spacer(Modifier.height(8.dp))
    }

    // While someone is picked, Back doesn't dismiss the box.
    BackHandler(enabled = result != null) {
        val choices = if (pickSnoozed) "Call, Text or Share" else "Call, Text, Share or Snooze"
        Toast.makeText(context, "Choose $choices first.", Toast.LENGTH_SHORT).show()
    }

    // Snooze choices
    val snoozed = result
    if (showSnooze && snoozed != null && !pickSnoozed) {
        AlertDialog(
            onDismissRequest = { showSnooze = false },
            title = { Text("Snooze ${snoozed.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showSnooze = false
                            if (Reminders.notificationsAllowed(context)) {
                                val at = Reminders.snoozeOneHour(context, snoozed.id)
                                repo.snoozeCurrentPick()
                                pickSnoozed = true
                                result = null
                                Toast.makeText(context, "I'll remind you ${timeLabel(at, repo.now())}", Toast.LENGTH_SHORT).show()
                            } else {
                                notifyAfterGrant = snoozed.id
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remind me in 1 hour") }
                    TextButton(
                        onClick = {
                            repo.skip(snoozed.id)
                            pickSnoozed = false
                            showSnooze = false
                            result = null
                            localVersion++
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back to pool") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSnooze = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResultBox(
    contact: Contact,
    look: AccessoryLook,
    onCall: () -> Unit,
    onText: () -> Unit,
    onShare: () -> Unit,
    onSnooze: () -> Unit,
    canSnooze: Boolean = true,
) {
    val hasNumber = contact.mainNumber != null
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .raised(RoundedCornerShape(30.dp), 24.dp),
    ) {
        // Text without its own color uses the card's text color (light text in dark mode).
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A small handle, like a sheet sliding up
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline),
            )
            Text(
                "YOUR PICK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
            )
            // Their picture in a raised ring
            Box(
                Modifier
                    .size(116.dp)
                    .raised(CircleShape, 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                ContactPicture(
                    contact,
                    look,
                    Modifier
                        .size(104.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                contact.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                contact.phoneLine() ?: "No phone number saved. Try Share instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionButton(AppIcons.Call, "Call", hasNumber, filled = true, onCall, Modifier.weight(1f))
                ActionButton(AppIcons.Text, "Text", hasNumber, filled = false, onText, Modifier.weight(1f))
                ActionButton(AppIcons.Share, "Share", true, filled = false, onShare, Modifier.weight(1f))
                ActionButton(AppIcons.Snooze, "Snooze", canSnooze, filled = false, onSnooze, Modifier.weight(1f))
            }
            if (!canSnooze) {
                Text(
                    "Snooze already used for this pick.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        }
    }
}

/** A round icon with its label underneath. Call is filled in; the others are soft grey. */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .raised(CircleShape, if (enabled) 8.dp else 2.dp, if (filled) Tone.Ink else Tone.Soft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EmptyWheel(hasPeople: Boolean, onAddPeople: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (hasPeople) "Everyone's been contacted." else "Your wheel is empty",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            if (hasPeople) "Add more people?" else "Add a few people to get started. You choose how.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        InkButton(onClick = onAddPeople, height = 52.dp) {
            Text("Add people", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** How far the round wheel leans back, in degrees. */
private const val WHEEL_TILT = 52f

/** "at 6:15 PM" today, or "tomorrow at 5:00 PM". */
private fun timeLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val time = t.format(DateTimeFormatter.ofPattern("h:mm a"))
    val days = t.toLocalDate().toEpochDay() - Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    return when (days) {
        0L -> "at $time"
        1L -> "tomorrow at $time"
        else -> t.format(DateTimeFormatter.ofPattern("EEE 'at' h:mm a"))
    }
}

// ---------- Handing off to other apps (no permissions needed) ----------

private fun openDialer(context: Context, number: String?) {
    if (number == null) return
    startOtherApp(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
}

private fun openTexting(context: Context, number: String?, message: String) {
    if (number == null) return
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
    if (message.isNotBlank()) intent.putExtra("sms_body", message) // Blank preset: nothing pre-filled
    startOtherApp(context, intent)
}

private fun openShare(context: Context, name: String, message: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
    if (message.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, message) // Blank preset: nothing pre-filled
    startOtherApp(context, Intent.createChooser(send, "Reach out to $name"))
}

private fun startOtherApp(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to do that.", Toast.LENGTH_SHORT).show()
    }
}
