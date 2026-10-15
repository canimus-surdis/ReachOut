package com.canimussurdis.reachout.ui.wheel

import android.Manifest
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
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
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
            val final = rotationToLandOn(target, count, rotation.value, turns = 5 + Random.nextInt(3))
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
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "This week's wheel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "New names every ${settings.batchDay.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.bodySmall,
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

                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 18.dp)
                            .alpha(wheelAlpha.value)
                            .semantics {
                                contentDescription = "Wheel with ${activeIndexes.size} names. Double tap to spin."
                            }
                            .pointerInput(slots, spinning) {
                                detectDragGestures(onDragEnd = { spin() }) { change, _ -> change.consume() }
                            }
                            .clickable(enabled = !spinning) { spin() },
                    ) {
                        drawWheel(
                            segments = segments,
                            rotation = rotation.value,
                            dark = dark,
                            pictureSeed = snap.wheel.drawnAt,
                            photo = photo,
                            showWholePicture = reveal && everyoneDone,
                            textMeasurer = textMeasurer,
                            textColor = textColor,
                            coveredColor = covered,
                        )
                        // Center hub
                        drawCircle(hubColor, size.minDimension * 0.09f, center)
                    }
                    // Pointer at the top
                    Canvas(
                        Modifier
                            .align(Alignment.TopCenter)
                            .size(width = 34.dp, height = 40.dp),
                    ) {
                        val p = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }
                        drawPath(p, pointerColor)
                        drawCircle(hubColor, size.width * 0.14f, Offset(size.width / 2f, size.height * 0.3f))
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
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
            ) {
                val person = result
                if (person != null) {
                    ResultBox(
                        contact = person,
                        look = resultLook,
                        onCall = {
                            repo.markReachedOut(person.id)
                            openDialer(context, person.mainNumber)
                            result = null
                        },
                        onText = {
                            repo.markReachedOut(person.id)
                            openTexting(context, person.mainNumber, settings.message)
                            result = null
                        },
                        onShare = {
                            repo.markReachedOut(person.id)
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
            Button(
                onClick = { spin() },
                enabled = !spinning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (spinning) "Spinning…" else "Spin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(onClick = onAddPeople, modifier = Modifier.fillMaxWidth()) { Text("+ Add more people") }
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
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Your pick",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ContactPicture(
                contact,
                look,
                Modifier
                    .size(132.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                contact.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("📞", "Call", hasNumber, onCall, Modifier.weight(1f))
                ActionButton("💬", "Text", hasNumber, onText, Modifier.weight(1f))
                ActionButton("↗️", "Share", true, onShare, Modifier.weight(1f))
                ActionButton("⏰", "Snooze", canSnooze, onSnooze, Modifier.weight(1f))
            }
            if (!canSnooze) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Snooze already used for this pick.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (!hasNumber) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No phone number saved. Try Share instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(emoji: String, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun EmptyWheel(hasPeople: Boolean, onAddPeople: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (hasPeople) "Everyone's been contacted." else "Your wheel is empty",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (hasPeople) "Add more people?" else "Add a few people to get started. You choose how.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddPeople) { Text("Add people") }
    }
}

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
