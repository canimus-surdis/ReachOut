package com.canimussurdis.reachout.ui.inspector

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.canimussurdis.reachout.ui.common.AccessoryLook
import com.canimussurdis.reachout.ui.common.AnimalAvatar
import com.canimussurdis.reachout.ui.common.animalBackground
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.canimussurdis.reachout.data.Contact
import com.canimussurdis.reachout.data.NotYetMode
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.data.ReachedMode
import com.canimussurdis.reachout.data.ReminderFrequency
import com.canimussurdis.reachout.reminders.Reminders
import com.canimussurdis.reachout.data.Schedule
import com.canimussurdis.reachout.data.SlotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Stage 2 test screen. Lets you exercise every rule (swipes, weekly batches, cooldowns,
 * checks, skip, refill vs reveal) with demo contacts and time travel, before the real
 * swipe cards and wheel are built. Later it moves under Settings → Demo mode.
 */
@Composable
fun DataInspectorScreen(repo: ReachOutRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val refresh: () -> Unit = { version++ }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        refresh()
    }

    val snap = remember(version) { repo.snapshot() }
    val canLoad = snap.settings.demoMode || hasPermission
    val deck by produceState(initialValue = emptyList<Contact>(), version, canLoad) {
        value = if (canLoad) {
            withContext(Dispatchers.IO) { runCatching { repo.loadSwipeDeck() }.getOrDefault(emptyList()) }
        } else {
            emptyList()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Test tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Until the wheel and Settings are built, this is where Continue and ☰ lead.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // ---- Demo mode ----
        item {
            Section("Demo mode") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (snap.settings.demoMode) "On: 20 fake contacts" else "Off: your real contacts")
                        Text(
                            "Demo data is kept separately and deleted when you turn this off.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = snap.settings.demoMode,
                        onCheckedChange = { repo.setDemoMode(it); refresh() },
                    )
                }
                if (snap.settings.demoMode) {
                    Text("Simulated now: ${dateTime(snap.now)}", fontWeight = FontWeight.SemiBold)
                    ButtonRow(
                        "+1 day" to { repo.timeTravel(Schedule.DAY_MS); refresh() },
                        "+1 week" to { repo.timeTravel(7 * Schedule.DAY_MS); refresh() },
                    )
                    ButtonRow(
                        "+1 month" to { repo.timeTravel(30 * Schedule.DAY_MS); refresh() },
                        "Next batch day" to { repo.jumpToNextBatchDay(); refresh() },
                    )
                    TextButton(onClick = { repo.resetDemo(); refresh() }) { Text("Reset demo") }
                }
            }
        }

        // ---- Permission ----
        if (!snap.settings.demoMode && !hasPermission) {
            item {
                Section("Contacts access") {
                    Text("Reach Out needs to read your contacts to show swipe cards. It can never change them.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Allow contacts access")
                    }
                }
            }
        }

        // ---- Did you connect? ----
        if (snap.pendingChecks.isNotEmpty()) {
            item {
                Section("Did you connect?") {
                    snap.pendingChecks.forEach { c ->
                        Text("Did you connect with ${c.name}?", fontWeight = FontWeight.SemiBold)
                        ButtonRow(
                            "Yes" to { repo.answerCheck(c.id, connected = true); refresh() },
                            "Not yet" to { repo.answerCheck(c.id, connected = false); refresh() },
                        )
                    }
                }
            }
        }

        // ---- Swipe deck ----
        item {
            Section("Swipe deck") {
                val top = deck.firstOrNull()
                Text("${deck.size} left to swipe")
                if (top != null) {
                    ContactLine(top)
                    ButtonRow(
                        "← Swipe left" to { repo.reject(top); refresh() },
                        "Swipe right →" to { repo.accept(top); refresh() },
                    )
                } else if (canLoad) {
                    Text("You've seen everyone.")
                }
            }
        }

        // ---- Wheel ----
        item {
            val modeLabel = if (snap.settings.reachedMode == ReachedMode.REFILL) "refill" else "reveal"
            Section("This week's wheel") {
                Text(
                    "Drawn ${dateTime(snap.wheel.drawnAt)} · ${snap.wheel.slots.size} spots · $modeLabel mode",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!snap.wheel.hasActiveNames) {
                    Text("Everyone's been contacted. Swipe more people?")
                }
                snap.wheel.slots.forEachIndexed { i, slot ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}. ${slot.contact.name}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when (slot.state) {
                            SlotState.ACTIVE -> {
                                TextButton(onClick = { repo.markReachedOut(slot.contact.id); refresh() }) {
                                    Text("Reached out")
                                }
                                TextButton(onClick = { repo.skip(slot.contact.id); refresh() }) {
                                    Text("Skip")
                                }
                            }
                            SlotState.REVEALED -> Text("revealed", style = MaterialTheme.typography.bodySmall)
                            SlotState.COVERED -> Text("covered", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(
                    "\"Reached out\" stands in for Call, Text or Share. The check appears at the top.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---- Cooldowns ----
        item {
            Section("In cooldown (${snap.cooldowns.size})") {
                if (snap.cooldowns.isEmpty()) Text("Nobody yet.")
                snap.cooldowns.forEach { cd ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cd.contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Back ${date(cd.returnsAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { repo.endCooldown(cd.contact.id); refresh() }) {
                            Text("Bring back now")
                        }
                    }
                }
            }
        }

        // ---- Accepted / Rejected ----
        item {
            Section("Accepted (${snap.accepted.size})") {
                snap.accepted.forEach { c ->
                    NameWithAction(c.name, "Remove") { repo.removeAccepted(c.id); refresh() }
                }
            }
        }
        item {
            Section("Rejected (${snap.rejected.size})") {
                snap.rejected.forEach { c ->
                    NameWithAction(c.name, "Restore") { repo.restoreRejected(c.id); refresh() }
                }
            }
        }

        // ---- Reminders ----
        item {
            val s = snap.settings
            fun change(update: (com.canimussurdis.reachout.data.Settings) -> com.canimussurdis.reachout.data.Settings) {
                repo.changeSettings(update)
                Reminders.rescheduleAll(context)
                refresh()
            }
            Section("Reminders") {
                if (!Reminders.notificationsAllowed(context)) {
                    Text(
                        "Notifications are off for Reach Out. Allow them in your phone's Settings → Apps → Reach Out → Notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                NameWithAction("Reminders: " + if (s.remindersOn) "On" else "Off", "Change") {
                    change { it.copy(remindersOn = !it.remindersOn) }
                }
                NameWithAction("How often: ${s.reminderFrequency.label}", "Change") {
                    change {
                        val all = ReminderFrequency.entries
                        it.copy(reminderFrequency = all[(all.indexOf(it.reminderFrequency) + 1) % all.size])
                    }
                }
                NameWithAction(
                    "Day: " + s.reminderDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    "Change",
                ) { change { it.copy(reminderDay = it.reminderDay.plus(1)) } }
                NameWithAction("Window starts: ${clock(s.windowStartMin)}", "Later") {
                    change {
                        val start = if (it.windowStartMin >= 20 * 60) 6 * 60 else it.windowStartMin + 60
                        it.copy(windowStartMin = start, windowEndMin = maxOf(it.windowEndMin, start + 60))
                    }
                }
                NameWithAction("Window ends: ${clock(s.windowEndMin)}", "Later") {
                    change {
                        val end = if (it.windowEndMin >= 23 * 60) it.windowStartMin + 60 else it.windowEndMin + 60
                        it.copy(windowEndMin = end)
                    }
                }
                Text(
                    "Next reminder: " + (s.nextReminderAt?.let { dateTime(it) } ?: "none"),
                    fontWeight = FontWeight.SemiBold,
                )
                if (s.snoozes.isNotEmpty()) {
                    Text(
                        "Snoozed: " + s.snoozes.entries.joinToString { (id, at) ->
                            (repo.contactById(id)?.name ?: "someone") + " at " + dateTime(at)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ButtonRow(
                    "Test reminder now" to { Reminders.showRegularNow(context) },
                    "Snooze test (1 min)" to {
                        snap.accepted.firstOrNull()?.let { Reminders.snoozeForTesting(context, it.id) }
                        refresh()
                    },
                )
                Text(
                    "In demo mode, reminders follow the pretend clock. Time travel past a planned reminder delivers it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---- Gallery: every accessory, each on a different animal ----
        item {
            Section("Animal & accessory gallery") {
                (0 until AccessoryLook.COUNT).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { i ->
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(animalBackground(i)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AnimalAvatar(index = i, look = AccessoryLook(i, i % 7), modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }

        // ---- Rules ----
        item {
            val s = snap.settings
            Section("Rules") {
                NameWithAction(
                    "Reached contacts: " +
                        if (s.reachedMode == ReachedMode.REFILL) "Refill with a new name" else "Reveal picture",
                    "Change",
                ) {
                    repo.setReachedMode(
                        if (s.reachedMode == ReachedMode.REFILL) ReachedMode.REVEAL else ReachedMode.REFILL
                    )
                    refresh()
                }
                NameWithAction(
                    "Not yet: " +
                        if (s.notYetMode == NotYetMode.NEXT_WEEK) "Back to pool" else "Keep on wheel",
                    "Change",
                ) {
                    repo.setNotYetMode(
                        if (s.notYetMode == NotYetMode.NEXT_WEEK) NotYetMode.KEEP_ON_WHEEL else NotYetMode.NEXT_WEEK
                    )
                    refresh()
                }
                NameWithAction(
                    "Batch day: " + s.batchDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    "Change",
                ) {
                    repo.setBatchDay(s.batchDay.plus(1))
                    refresh()
                }
                NameWithAction("Spin sound: " + if (s.spinSound) "On" else "Off", "Change") {
                    repo.changeSettings { it.copy(spinSound = !it.spinSound) }
                    refresh()
                }
                NameWithAction("Spin vibration: " + if (s.spinVibration) "On" else "Off", "Change") {
                    repo.changeSettings { it.copy(spinVibration = !it.spinVibration) }
                    refresh()
                }
                NameWithAction("Reduce motion: " + if (s.reduceMotion) "On" else "Off", "Change") {
                    repo.changeSettings { it.copy(reduceMotion = !it.reduceMotion) }
                    refresh()
                }
                Text(
                    "Cooldown: ${s.cooldownMinDays}–${s.cooldownMaxDays} days · wheel size ${s.wheelSize}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ButtonRow(first: Pair<String, () -> Unit>, second: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = first.second, modifier = Modifier.weight(1f)) { Text(first.first) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = second.second, modifier = Modifier.weight(1f)) { Text(second.first) }
    }
}

@Composable
private fun NameWithAction(text: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun ContactLine(c: Contact) {
    Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    val picture = if (c.photoUri != null) "Has photo" else "Animal #${c.animalIndex + 1}"
    Text("$picture · ${c.mainNumber ?: "No number"}", style = MaterialTheme.typography.bodySmall)
}

private val dateTimeFormat = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")
private val dateFormat = DateTimeFormatter.ofPattern("EEE MMM d, yyyy")

private fun dateTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

private fun clock(minuteOfDay: Int): String =
    java.time.LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun date(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateFormat)
