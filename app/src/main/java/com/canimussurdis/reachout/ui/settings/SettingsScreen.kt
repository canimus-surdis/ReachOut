package com.canimussurdis.reachout.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.canimussurdis.reachout.data.Backup
import com.canimussurdis.reachout.data.Contact
import com.canimussurdis.reachout.data.NotYetMode
import com.canimussurdis.reachout.data.PictureSource
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.data.ReachedMode
import com.canimussurdis.reachout.data.ReminderFrequency
import com.canimussurdis.reachout.data.Schedule
import com.canimussurdis.reachout.data.Settings
import com.canimussurdis.reachout.data.Snapshot
import com.canimussurdis.reachout.data.Status
import com.canimussurdis.reachout.data.VCard
import com.canimussurdis.reachout.data.ThemeMode
import com.canimussurdis.reachout.data.WheelPhotos
import com.canimussurdis.reachout.reminders.Reminders
import com.canimussurdis.reachout.ui.theme.paletteSwatches
import com.canimussurdis.reachout.data.ColorPalette
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import java.time.Month
import java.time.LocalDate
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Page(val title: String) {
    MAIN("Settings"),
    CONTACTS("Contacts"),
    IMPORT("Import contacts"),
    PICK("Choose people"),
    BREAKS("Custom break lengths"),
    IGNORE("Ignore contacts"),
    WHEEL("Wheel"),
    REMINDERS("Reminders"),
    MESSAGES("Messages"),
    DISPLAY("Display"),
    BACKUP("Backup & restore"),
    DEMO("Demo mode & testing"),
    GUIDE("How-to guide"),
}

/** Break lengths offered for cooldowns, in days. */
private val BREAK_OPTIONS = listOf(7, 14, 21, 30, 61, 91, 122, 182, 273, 365)

private val TEXT_SCALES = listOf(1f, 1.15f, 1.3f)
private val TEXT_SCALE_LABELS = listOf("Normal", "Large", "Extra large")

/**
 * The ☰ menu. Every change is saved straight away and only affects the app's own storage.
 * [onSettingsChanged] lets the rest of the app pick up theme / text size / data changes.
 */
@Composable
fun SettingsScreen(
    repo: ReachOutRepository,
    onBack: () -> Unit,
    onOpenTestTools: () -> Unit,
    onSettingsChanged: () -> Unit,
    /** Replays the welcome tour. */
    onShowTour: () -> Unit,
    modifier: Modifier = Modifier,
    /** Open straight to Import contacts (from the wheel's "Add more people" when nobody's left to swipe). */
    startAtImport: Boolean = false,
    /** Import contacts: swipe on the people you picked (instead of adding them straight to your list). */
    onSwipePeople: (List<Contact>) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entryPage = if (startAtImport) Page.IMPORT else Page.MAIN
    var page by rememberSaveable { mutableStateOf(entryPage) }
    var version by remember { mutableIntStateOf(0) }
    val s = remember(version) { repo.settings }
    val snap = remember(version) { repo.snapshot() }

    fun changed() {
        version++
        onSettingsChanged()
    }

    fun update(reschedule: Boolean = false, change: (Settings) -> Settings) {
        repo.changeSettings(change)
        if (reschedule) Reminders.rescheduleAll(context)
        changed()
    }

    // Set when a page was opened from the How-to guide, so Back returns to the guide.
    var backToGuide by rememberSaveable { mutableStateOf(false) }

    fun goBack() {
        when (page) {
            entryPage -> onBack()
            Page.MAIN -> onBack()
            Page.PICK -> page = Page.IMPORT
            Page.BREAKS -> page = Page.CONTACTS
            Page.IGNORE -> page = Page.CONTACTS
            Page.IMPORT -> page = Page.CONTACTS
            Page.GUIDE -> page = Page.MAIN
            else -> {
                page = if (backToGuide) Page.GUIDE else Page.MAIN
                backToGuide = false
            }
        }
    }
    BackHandler { goBack() }

    // Photo picker for the hidden wheel picture (no permission needed).
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val names = withContext(Dispatchers.IO) { WheelPhotos.import(context, uris) }
                update { it.copy(wheelPhotos = it.wheelPhotos + names) }
            }
        }
    }

    // People not swiped yet, for Import contacts.
    val deck by produceState<List<Contact>?>(null, page, version) {
        if (page == Page.IMPORT || page == Page.PICK) {
            value = withContext(Dispatchers.IO) {
                runCatching { repo.loadSwipeDeck().sortedBy { it.name.lowercase() } }.getOrNull()
            }
        }
    }
    var query by rememberSaveable { mutableStateOf("") }

    // ---- Backup & restore ----
    var includePhotos by rememberSaveable { mutableStateOf(true) }
    var pendingRestore by remember { mutableStateOf<Backup.Contents?>(null) }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val summary = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { Backup.export(context, it, includePhotos) }
                    }.getOrNull()
                }
                toast(
                    if (summary == null) "Couldn't save the backup. Try another location."
                    else "Backup saved: ${summary.people} people" +
                        if (summary.photos > 0) " and ${summary.photos} photos." else "."
                )
            }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { Backup.read(it) }
                            ?: throw IllegalArgumentException("Couldn't open that file.")
                    }
                }
                result.onSuccess { pendingRestore = it }
                    .onFailure { toast(it.message ?: "That file couldn't be read.") }
            }
        }
    }

    // ---- Import contacts ----
    // null = choosing from the phone; a list = people found in a .vcf file.
    var fileCandidates by remember { mutableStateOf<List<Contact>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmAddAll by remember { mutableStateOf(false) }
    val canRead = s.demoMode || hasContactsPermission(context)

    val openVcf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val parsed = context.contentResolver.openInputStream(uri)?.use { VCard.read(it) }
                            ?: throw IllegalArgumentException("Couldn't open that file.")
                        // If someone in the file is also in your phone, use the phone's copy (it has their photo).
                        val phone = repo.loadAllContacts()
                        val known = repo.knownIds()
                        fun digits(n: String) = n.filter { it.isDigit() }.takeLast(10)
                        parsed.map { f ->
                            val numbers = f.phones.map { digits(it.number) }.toSet()
                            phone.firstOrNull { p ->
                                p.name.equals(f.name, ignoreCase = true) &&
                                    (p.phones.isEmpty() && numbers.isEmpty() || p.phones.any { digits(it.number) in numbers })
                            } ?: f
                        }.distinctBy { it.id }.filter { it.id !in known }.sortedBy { it.name.lowercase() }
                    }
                }
                result.onSuccess { list ->
                    if (list.isEmpty()) {
                        toast("No new people found in that file.")
                    } else {
                        fileCandidates = list
                        selected = list.map { it.id }.toSet()
                        query = ""
                        page = Page.PICK
                    }
                }.onFailure { toast(it.message ?: "That file couldn't be read.") }
            }
        }
    }

    // ---- Custom break lengths ----
    var breakSelected by remember { mutableStateOf(setOf<String>()) }
    var breakMin by remember { mutableIntStateOf(s.cooldownMinDays) }
    var breakMax by remember { mutableIntStateOf(s.cooldownMaxDays) }
    var breakQuery by rememberSaveable { mutableStateOf("") }

    // ---- Ignore contacts ----
    var ignoreSelected by remember { mutableStateOf(setOf<String>()) }
    var ignoreQuery by rememberSaveable { mutableStateOf("") }
    // Everyone in your contacts (or the demo list) who isn't ignored yet, with what they are now.
    val ignoreCandidates by produceState<List<Contact>?>(null, page, version) {
        if (page == Page.IGNORE) {
            value = withContext(Dispatchers.IO) {
                val ignored = snap.ignored.map { it.id }.toSet()
                repo.loadAllContacts().filter { it.id !in ignored }.sortedBy { it.name.lowercase() }
            }
        }
    }
    fun openIgnore() {
        ignoreSelected = emptySet()
        ignoreQuery = ""
        page = Page.IGNORE
    }

    // ---- How-to guide ----
    var guideOpen by remember { mutableStateOf(setOf<Int>()) }
    var guideQuery by rememberSaveable { mutableStateOf("") }

    // Message editor: null = closed, -1 = new message, else index of the custom message being edited.
    var editing by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }

    // Import asks for contacts access itself (read only), then carries on with what you tapped.
    fun openPick() {
        fileCandidates = null
        selected = emptySet()
        query = ""
        page = Page.PICK
    }
    var afterAccess by remember { mutableStateOf<String?>(null) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val next = afterAccess
        afterAccess = null
        changed()
        if (!granted) {
            toast("Contacts access is off. Allow it in your phone's Settings → Apps → Reach Out → Permissions, or import from a file.")
        } else if (next == "pick") {
            openPick()
        } else if (next == "all") {
            confirmAddAll = true
        } else if (next == "ignore") {
            openIgnore()
        }
    }
    fun withContacts(next: String) {
        afterAccess = next
        askContacts.launch(Manifest.permission.READ_CONTACTS)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { goBack() }) { Text("‹ Back") }
            Text(
                page.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                Page.MAIN -> mainPage(s, snap, onShowTour) { page = it }
                Page.GUIDE -> guidePage(
                    query = guideQuery,
                    onQuery = { guideQuery = it },
                    expanded = guideOpen,
                    onToggle = { i -> guideOpen = if (i in guideOpen) guideOpen - i else guideOpen + i },
                    onExpandAll = { all -> guideOpen = if (all) GUIDE.indices.toSet() else emptySet() },
                    onOpen = { target ->
                        if (target == GuideTarget.TOUR) {
                            onShowTour()
                        } else {
                            if (target == GuideTarget.BREAKS) {
                                breakSelected = emptySet()
                                breakMin = s.cooldownMinDays
                                breakMax = s.cooldownMaxDays
                                breakQuery = ""
                            }
                            // Back returns to the guide. (Import and Custom break lengths step back via Contacts.)
                            backToGuide = true
                            page = when (target) {
                                GuideTarget.CONTACTS -> Page.CONTACTS
                                GuideTarget.IMPORT -> Page.IMPORT
                                GuideTarget.BREAKS -> Page.BREAKS
                                GuideTarget.WHEEL -> Page.WHEEL
                                GuideTarget.REMINDERS -> Page.REMINDERS
                                GuideTarget.MESSAGES -> Page.MESSAGES
                                GuideTarget.DISPLAY -> Page.DISPLAY
                                GuideTarget.BACKUP -> Page.BACKUP
                                GuideTarget.DEMO -> Page.DEMO
                                GuideTarget.TOUR -> Page.MAIN
                            }
                        }
                    },
                )
                Page.CONTACTS -> contactsPage(
                    snap,
                    onAdd = { page = Page.IMPORT },
                    onCustomBreaks = {
                        breakSelected = emptySet()
                        breakMin = s.cooldownMinDays
                        breakMax = s.cooldownMaxDays
                        breakQuery = ""
                        page = Page.BREAKS
                    },
                    onBringBack = { repo.endCooldown(it); changed() },
                    onRemove = { repo.removeAccepted(it); changed() },
                    onRestore = { repo.restoreRejected(it); changed() },
                    onIgnoreContacts = {
                        if (canRead) {
                            openIgnore()
                        } else {
                            withContacts("ignore")
                        }
                    },
                    onUnignore = { repo.unignore(it); changed() },
                )
                Page.IGNORE -> ignorePage(
                    candidates = ignoreCandidates,
                    statuses = repo.statuses(),
                    query = ignoreQuery,
                    onQuery = { ignoreQuery = it },
                    selected = ignoreSelected,
                    onToggle = { id -> ignoreSelected = if (id in ignoreSelected) ignoreSelected - id else ignoreSelected + id },
                    onSelectAll = { ids -> ignoreSelected = ignoreSelected + ids },
                    onClear = { ignoreSelected = emptySet() },
                    onIgnore = {
                        val chosen = ignoreCandidates.orEmpty().filter { it.id in ignoreSelected }
                        repo.ignore(chosen)
                        toast(if (chosen.size == 1) "Ignored 1 person." else "Ignored ${chosen.size} people.")
                        ignoreSelected = emptySet()
                        changed()
                        page = Page.CONTACTS
                    },
                )
                Page.BREAKS -> breaksPage(
                    snap = snap,
                    defaultMin = s.cooldownMinDays,
                    defaultMax = s.cooldownMaxDays,
                    minDays = breakMin,
                    maxDays = breakMax,
                    onMin = { breakMin = it; if (breakMax < it) { breakMax = it } },
                    onMax = { breakMax = it; if (breakMin > it) { breakMin = it } },
                    query = breakQuery,
                    onQuery = { breakQuery = it },
                    selected = breakSelected,
                    onToggle = { id -> breakSelected = if (id in breakSelected) breakSelected - id else breakSelected + id },
                    onSelectAll = { ids -> breakSelected = breakSelected + ids },
                    onClear = { breakSelected = emptySet() },
                    onApply = {
                        repo.setCustomBreak(breakSelected, breakMin, breakMax)
                        toast("Saved for ${breakSelected.size} " + if (breakSelected.size == 1) "person." else "people.")
                        breakSelected = emptySet()
                        changed()
                    },
                    onReset = {
                        repo.setCustomBreak(breakSelected, null, null)
                        toast("Back to the default for ${breakSelected.size} " + if (breakSelected.size == 1) "person." else "people.")
                        breakSelected = emptySet()
                        changed()
                    },
                )
                Page.IMPORT -> importPage(
                    showOutOfPeople = canRead && deck?.isEmpty() == true,
                    canRead = canRead,
                    newCount = deck?.size,
                    onPick = {
                        if (canRead) {
                            openPick()
                        } else {
                            withContacts("pick")
                        }
                    },
                    onAddAll = {
                        if (canRead) {
                            confirmAddAll = true
                        } else {
                            withContacts("all")
                        }
                    },
                    onFile = { openVcf.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream")) },
                )
                Page.PICK -> pickPage(
                    candidates = fileCandidates ?: deck,
                    fromFile = fileCandidates != null,
                    query = query,
                    onQuery = { query = it },
                    selected = selected,
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    onSelectAll = { ids -> selected = selected + ids },
                    onClear = { selected = emptySet() },
                    onAdd = {
                        val chosen = (fileCandidates ?: deck).orEmpty().filter { it.id in selected }
                        selected = emptySet()
                        fileCandidates = null
                        page = Page.IMPORT
                        onSwipePeople(chosen)
                    },
                )
                Page.WHEEL -> wheelPage(
                    s,
                    update = { f -> update(change = f) },
                    onReachedMode = { repo.setReachedMode(it); changed() },
                    onAddPhotos = {
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRemovePhoto = { name ->
                        WheelPhotos.delete(context, name)
                        update { it.copy(wheelPhotos = it.wheelPhotos - name) }
                    },
                )
                Page.REMINDERS -> remindersPage(s, context) { f -> update(reschedule = true, change = f) }
                Page.MESSAGES -> messagesPage(
                    s,
                    onSelect = { text -> update { it.copy(message = text) } },
                    onNew = { draft = ""; editing = -1 },
                    onEdit = { i -> draft = s.customMessages[i]; editing = i },
                    onDelete = { i ->
                        update {
                            val removed = it.customMessages[i]
                            it.copy(
                                customMessages = it.customMessages.filterIndexed { k, _ -> k != i },
                                message = if (it.message == removed) Settings.DEFAULT_MESSAGE else it.message,
                            )
                        }
                    },
                )
                Page.DISPLAY -> displayPage(s) { f -> update(change = f) }
                Page.BACKUP -> backupPage(
                    includePhotos = includePhotos,
                    onIncludePhotos = { includePhotos = it },
                    onSave = { saveBackup.launch(Backup.suggestedFileName()) },
                    onRestore = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
                Page.DEMO -> demoPage(
                    s,
                    now = snap.now,
                    onDemo = { repo.setDemoMode(it); changed() },
                    onTravel = { repo.timeTravel(it); changed() },
                    onNextBatch = { repo.jumpToNextBatchDay(); changed() },
                    onReset = { repo.resetDemo(); changed() },
                    onTestReminder = {
                        if (Reminders.notificationsAllowed(context)) {
                            Reminders.showRegularNow(context)
                        } else {
                            toast("Notifications are off for Reach Out. Turn them on in Settings → Reminders.")
                        }
                    },
                    onOpenTestTools = onOpenTestTools,
                )
            }
        }
    }

    // Confirm before replacing everything with a backup
    val restoring = pendingRestore
    if (restoring != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "It has ${restoring.people} people on the list. Restoring replaces your current list, " +
                        "cooldowns, messages and settings. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val summary = withContext(Dispatchers.IO) {
                            runCatching { Backup.restore(context, restoring) }.getOrNull()
                        }
                        if (summary == null) {
                            toast("Restore failed. Nothing was changed.")
                        } else {
                            Reminders.rescheduleAll(context)
                            changed()
                            toast("Restored ${summary.people} people.")
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    // Add everyone from the phone
    if (confirmAddAll) {
        val everyone = deck.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmAddAll = false },
            title = { Text("Swipe through everyone?") },
            text = {
                Text(
                    when {
                        deck == null -> "Counting the people you haven't swiped on…"
                        everyone.isEmpty() -> "You've already swiped on everyone in your phone."
                        else -> "Shows all ${everyone.size} people you haven't swiped on as swipe cards, " +
                            "so you can keep or pass on each one."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = everyone.isNotEmpty(),
                    onClick = {
                        confirmAddAll = false
                        onSwipePeople(everyone)
                    },
                ) { Text("Start swiping") }
            },
            dismissButton = { TextButton(onClick = { confirmAddAll = false }) { Text("Cancel") } },
        )
    }

    // Write / edit a custom message
    val editIndex = editing
    if (editIndex != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (editIndex == -1) "New message" else "Edit message") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val text = draft.trim()
                        update {
                            if (editIndex == -1) {
                                it.copy(customMessages = it.customMessages + text, message = text)
                            } else {
                                val old = it.customMessages[editIndex]
                                it.copy(
                                    customMessages = it.customMessages.mapIndexed { k, m -> if (k == editIndex) text else m },
                                    message = if (it.message == old) text else it.message,
                                )
                            }
                        }
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

// =====================================================================
// Pages
// =====================================================================

private fun LazyListScope.mainPage(s: Settings, snap: Snapshot, onShowTour: () -> Unit, open: (Page) -> Unit) {
    item {
        Group {
            NavRow(
                "Contacts",
                "${snap.accepted.size} on your list · ${snap.cooldowns.size} in cooldown · ${snap.rejected.size} passed",
            ) { open(Page.CONTACTS) }
            NavRow(
                "Wheel",
                "New names every ${dayName(s.batchDay)} · " +
                    if (s.reachedMode == ReachedMode.REFILL) "refill with new names" else "reveal a picture",
            ) { open(Page.WHEEL) }
            NavRow(
                "Reminders",
                if (s.remindersOn) "${s.reminderFrequency.label} · ${clock(s.windowStartMin)}–${clock(s.windowEndMin)}" else "Off",
            ) { open(Page.REMINDERS) }
            NavRow("Messages", if (s.message.isBlank()) Settings.messageLabel(s.message) else "\"${s.message}\"") { open(Page.MESSAGES) }
            NavRow(
                "Display",
                "${s.palette.label} · ${s.themeMode.label} · ${TEXT_SCALE_LABELS[nearestIndex(TEXT_SCALES, s.textScale)]} text",
            ) { open(Page.DISPLAY) }
            NavRow("Backup & restore", "Save your list and settings to a file") { open(Page.BACKUP) }
            NavRow("Demo mode & testing", if (s.demoMode) "Demo mode is on" else "Off") { open(Page.DEMO) }
        }
    }
    item {
        Group("Help") {
            NavRow("How-to guide", "What every setting does, in plain words") { open(Page.GUIDE) }
            NavRow("Welcome tour", "See the quick walkthrough again") { onShowTour() }
        }
    }
    item {
        Note("Reach Out only reads your contacts. It never changes or deletes them.")
    }
}

private fun LazyListScope.contactsPage(
    snap: Snapshot,
    onAdd: () -> Unit,
    onCustomBreaks: () -> Unit,
    onBringBack: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRestore: (String) -> Unit,
    onIgnoreContacts: () -> Unit,
    onUnignore: (String) -> Unit,
) {
    item {
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ Import contacts") }
    }
    item {
        OutlinedButton(onClick = onIgnoreContacts, modifier = Modifier.fillMaxWidth()) { Text("Ignore contacts") }
    }
    item {
        OutlinedButton(onClick = onCustomBreaks, modifier = Modifier.fillMaxWidth()) {
            Text("Custom break lengths" + if (snap.customBreaks.isEmpty()) "" else " (${snap.customBreaks.size} set)")
        }
    }

    item { Header("In cooldown (${snap.cooldowns.size})") }
    if (snap.cooldowns.isEmpty()) item { Note("Nobody right now.") }
    items(snap.cooldowns, key = { "cd-" + it.contact.id }) { cd ->
        PersonRow(cd.contact.name, "Back ${shortDate(cd.returnsAt)}", "Bring back now") { onBringBack(cd.contact.id) }
    }

    item { Header("On your list (${snap.accepted.size})") }
    item { Note("Removing someone sends them back to swiping: you'll see them the next time you open the app.") }
    items(snap.accepted, key = { "ac-" + it.id }) { c ->
        val custom = snap.customBreaks[c.id]
        PersonRow(c.name, custom?.let { "Break: ${breakRange(it.first, it.second)}" }, "Remove") { onRemove(c.id) }
    }

    item { Header("Passed (${snap.rejected.size})") }
    if (snap.rejected.isEmpty()) item { Note("Nobody yet.") }
    items(snap.rejected, key = { "rj-" + it.id }) { c ->
        PersonRow(c.name, null, "Restore") { onRestore(c.id) }
    }

    item { Header("Ignored (${snap.ignored.size})") }
    item { Note("Ignored people never show up as swipe cards or in imports. Un-ignore to swipe on them again.") }
    if (snap.ignored.isEmpty()) item { Note("Nobody yet.") }
    items(snap.ignored, key = { "ig-" + it.id }) { c ->
        PersonRow(c.name, null, "Un-ignore") { onUnignore(c.id) }
    }
}

private fun LazyListScope.ignorePage(
    candidates: List<Contact>?,
    statuses: Map<String, Status>,
    query: String,
    onQuery: (String) -> Unit,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onClear: () -> Unit,
    onIgnore: () -> Unit,
) {
    item {
        Note(
            "Tick people you never want to see as swipe cards, such as businesses or old numbers. " +
                "Anyone on your list comes off it. Un-ignore them any time on the Contacts page."
        )
    }
    item {
        Button(onClick = onIgnore, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(
                when (selected.size) {
                    0 -> "Choose people to ignore"
                    1 -> "Ignore 1 person"
                    else -> "Ignore ${selected.size} people"
                }
            )
        }
    }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (candidates == null) {
        item { Note("Loading…") }
        return
    }
    val shown = if (query.isBlank()) candidates else candidates.filter { it.name.contains(query.trim(), ignoreCase = true) }
    if (shown.isEmpty()) {
        item { Note(if (candidates.isEmpty()) "Nobody left to ignore." else "No matches.") }
        return
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelectAll(shown.map { it.id }) }) { Text("Select all (${shown.size})") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    items(shown, key = { "ign-" + it.id }) { c ->
        val now = when (statuses[c.id]) {
            Status.ACCEPTED -> "On your list"
            Status.REJECTED -> "Passed"
            else -> null
        }
        val detail = listOfNotNull(now, c.mainNumber).joinToString(" · ").ifBlank { null }
        CheckRow(c.name, detail, checked = c.id in selected) { onToggle(c.id) }
    }
}

private fun LazyListScope.breaksPage(
    snap: Snapshot,
    defaultMin: Int,
    defaultMax: Int,
    minDays: Int,
    maxDays: Int,
    onMin: (Int) -> Unit,
    onMax: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    item {
        Note(
            "Choose one or more people, set their break, then tap Apply. Everyone else keeps the default " +
                "(${breakRange(defaultMin, defaultMax)}). New lengths start the next time you connect."
        )
    }
    item {
        Group("Break for the people you choose") {
            val minI = nearestIndex(BREAK_OPTIONS, minDays)
            val maxI = nearestIndex(BREAK_OPTIONS, maxDays)
            Stepper(
                "Shortest",
                breakLabel(BREAK_OPTIONS[minI]),
                canMinus = minI > 0,
                canPlus = minI < BREAK_OPTIONS.lastIndex,
                onMinus = { onMin(BREAK_OPTIONS[minI - 1]) },
                onPlus = { onMin(BREAK_OPTIONS[minI + 1]) },
            )
            Stepper(
                "Longest",
                breakLabel(BREAK_OPTIONS[maxI]),
                canMinus = maxI > 0,
                canPlus = maxI < BREAK_OPTIONS.lastIndex,
                onMinus = { onMax(BREAK_OPTIONS[maxI - 1]) },
                onPlus = { onMax(BREAK_OPTIONS[maxI + 1]) },
            )
            val n = selected.size
            val who = if (n == 1) "1 person" else "$n people"
            Button(onClick = onApply, enabled = n > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (n == 0) "Choose people below" else "Apply to $who")
            }
            OutlinedButton(onClick = onReset, enabled = n > 0, modifier = Modifier.fillMaxWidth()) {
                Text(if (n == 0) "Reset to default" else "Reset $who to default")
            }
        }
    }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val shown = if (query.isBlank()) snap.accepted else snap.accepted.filter { it.name.contains(query.trim(), ignoreCase = true) }
    if (shown.isEmpty()) {
        item { Note(if (snap.accepted.isEmpty()) "Nobody on your list yet." else "No matches.") }
        return
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelectAll(shown.map { it.id }) }) { Text("Select all (${shown.size})") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    items(shown, key = { "br-" + it.id }) { c ->
        val custom = snap.customBreaks[c.id]
        CheckRow(
            c.name,
            if (custom != null) "Custom: ${breakRange(custom.first, custom.second)}" else "Default",
            checked = c.id in selected,
        ) { onToggle(c.id) }
    }
}

private fun LazyListScope.importPage(
    showOutOfPeople: Boolean,
    canRead: Boolean,
    newCount: Int?,
    onPick: () -> Unit,
    onAddAll: () -> Unit,
    onFile: () -> Unit,
) {
    if (showOutOfPeople) {
        item {
            Group {
                Text(
                    "You've swiped on everyone in your phone.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Note("Add more people from a contacts file, or add anyone you haven't swiped on.")
            }
        }
    }
    item {
        Group {
            NavRow("Pick several at once", "Tick people from your phone's contacts, then swipe on them") { onPick() }
            NavRow(
                "Add everyone",
                when {
                    !canRead -> "Swipe on everyone from your phone you haven't swiped on yet"
                    newCount == null -> "Counting…"
                    newCount == 1 -> "Swipe on the 1 person you haven't swiped on yet"
                    else -> "Swipe on all $newCount people you haven't swiped on yet"
                },
            ) { onAddAll() }
            NavRow("From a contacts file (.vcf)", "Exported from Google Contacts, iCloud, Outlook and others") { onFile() }
        }
    }
    if (!canRead) {
        item { Note("The first two ask for contacts access (read only) the first time you use them. Importing from a file works without it.") }
    }
    item {
        Note("Your phone's contacts are never changed. People already on your list, or ones you passed on, are skipped.")
    }
}

private fun LazyListScope.pickPage(
    candidates: List<Contact>?,
    fromFile: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onClear: () -> Unit,
    onAdd: () -> Unit,
) {
    item {
        Note(
            if (fromFile) "Found ${candidates?.size ?: 0} new people in the file. Untick anyone you don't want to swipe on."
            else "Tick the people you'd like to swipe on. You'll keep or pass on each one next."
        )
    }
    item {
        Button(onClick = onAdd, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(
                when (selected.size) {
                    0 -> "Choose people"
                    1 -> "Swipe on 1 person"
                    else -> "Swipe on ${selected.size} people"
                }
            )
        }
    }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (candidates == null) {
        item { Note("Loading…") }
        return
    }
    val shown = if (query.isBlank()) candidates else candidates.filter { it.name.contains(query.trim(), ignoreCase = true) }
    if (shown.isEmpty()) {
        item { Note(if (candidates.isEmpty()) "You've already swiped on everyone." else "No matches.") }
        return
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelectAll(shown.map { it.id }) }) { Text("Select all (${shown.size})") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
    items(shown, key = { "pick-" + it.id }) { c ->
        CheckRow(c.name, c.mainNumber, checked = c.id in selected) { onToggle(c.id) }
    }
}

private fun LazyListScope.wheelPage(
    s: Settings,
    update: ((Settings) -> Settings) -> Unit,
    onReachedMode: (ReachedMode) -> Unit,
    onAddPhotos: () -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    item {
        Group("New names each week on") {
            Choices(
                DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                selected = s.batchDay.ordinal,
                perRow = 4,
            ) { i -> update { it.copy(batchDay = DayOfWeek.entries[i]) } }
        }
    }
    item {
        Group("Break after you connect") {
            Note(
                "Each person you connect with rests for a random time between these two lengths. " +
                    "To give some people their own lengths, use Contacts → Custom break lengths."
            )
            val minI = nearestIndex(BREAK_OPTIONS, s.cooldownMinDays)
            val maxI = nearestIndex(BREAK_OPTIONS, s.cooldownMaxDays)
            Stepper(
                "Shortest",
                breakLabel(BREAK_OPTIONS[minI]),
                canMinus = minI > 0,
                canPlus = minI < maxI,
                onMinus = { update { it.copy(cooldownMinDays = BREAK_OPTIONS[minI - 1]) } },
                onPlus = { update { it.copy(cooldownMinDays = BREAK_OPTIONS[minI + 1]) } },
            )
            Stepper(
                "Longest",
                breakLabel(BREAK_OPTIONS[maxI]),
                canMinus = maxI > minI,
                canPlus = maxI < BREAK_OPTIONS.lastIndex,
                onMinus = { update { it.copy(cooldownMaxDays = BREAK_OPTIONS[maxI - 1]) } },
                onPlus = { update { it.copy(cooldownMaxDays = BREAK_OPTIONS[maxI + 1]) } },
            )
        }
    }
    item {
        Group("When you reach someone") {
            Choices(listOf("New name", "Reveal picture"), selected = s.reachedMode.ordinal, perRow = 2) { i ->
                onReachedMode(ReachedMode.entries[i])
            }
            Note(
                if (s.reachedMode == ReachedMode.REFILL) {
                    "A random new name takes their spot on the wheel."
                } else {
                    "Their slice of a hidden picture is uncovered. Reach everyone to see it all."
                }
            )
        }
    }
    item {
        Group("When you answer \"Not yet\"") {
            Choices(listOf("Back to pool", "Keep on wheel"), selected = s.notYetMode.ordinal, perRow = 2) { i ->
                update { it.copy(notYetMode = NotYetMode.entries[i]) }
            }
            Note(
                if (s.notYetMode == NotYetMode.NEXT_WEEK) "Off this week's wheel; back in the pool from the next batch day."
                else "They stay on this week's wheel."
            )
        }
    }
    item {
        Group("Hidden picture") {
            if (s.reachedMode != ReachedMode.REVEAL) Note("Used when \"Reveal picture\" is on.")
            Choices(PictureSource.entries.map { it.label }, selected = s.pictureSource.ordinal, perRow = 3) { i ->
                update { it.copy(pictureSource = PictureSource.entries[i]) }
            }
            if (s.pictureSource != PictureSource.DESIGNS && s.wheelPhotos.isEmpty()) {
                Note("Add a few photos, or the wheel will use designs.")
            }
            s.wheelPhotos.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { name -> PhotoThumb(name) { onRemovePhoto(name) } }
                }
            }
            OutlinedButton(onClick = onAddPhotos, modifier = Modifier.fillMaxWidth()) { Text("Add photos") }
            Note("Copies are kept inside the app. Your gallery isn't changed.")
        }
    }
}

private fun LazyListScope.remindersPage(s: Settings, context: Context, update: ((Settings) -> Settings) -> Unit) {
    item {
        Group {
            ToggleRow("Reminders", "A nudge to spin the wheel", s.remindersOn) { on -> update { it.copy(remindersOn = on) } }
        }
    }
    if (!s.remindersOn) return
    if (!Reminders.notificationsAllowed(context)) {
        item {
            Group("Notifications are off") {
                Note("Reach Out can't show reminders until notifications are allowed.")
                Button(onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }) { Text("Open notification settings") }
            }
        }
    }
    item {
        Group("How often") {
            Choices(ReminderFrequency.entries.map { it.label }, selected = s.reminderFrequency.ordinal, perRow = 3) { i ->
                update {
                    val f = ReminderFrequency.entries[i]
                    // Yearly needs at least one month: start with this month.
                    val months = if (f == ReminderFrequency.YEARLY && it.reminderMonths.isEmpty()) {
                        setOf(LocalDate.now().month)
                    } else {
                        it.reminderMonths
                    }
                    // Monthly needs a date: start with today's.
                    val dom = if (f == ReminderFrequency.MONTHLY && it.reminderDayOfMonth == 0) {
                        LocalDate.now().dayOfMonth
                    } else {
                        it.reminderDayOfMonth
                    }
                    it.copy(reminderFrequency = f, reminderMonths = months, reminderDayOfMonth = dom)
                }
            }
        }
    }
    val dayLabels = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    when (s.reminderFrequency) {
        ReminderFrequency.DAILY -> Unit
        ReminderFrequency.WEEKLY, ReminderFrequency.BIWEEKLY -> item {
            Group("On these days") {
                MultiChoices(
                    dayLabels,
                    selected = s.reminderDays.map { it.ordinal }.toSet(),
                    perRow = 4,
                ) { i ->
                    update {
                        val day = DayOfWeek.entries[i]
                        val days = if (day in it.reminderDays) it.reminderDays - day else it.reminderDays + day
                        if (days.isEmpty()) it else it.copy(reminderDays = days) // keep at least one day
                    }
                }
                Note(
                    (if (s.reminderFrequency == ReminderFrequency.WEEKLY) "Every week" else "Every other week") +
                        ", on each day you pick. Tap a day to add or remove it."
                )
            }
        }
        ReminderFrequency.MONTHLY -> item { MonthlyDate(s, update) }
        ReminderFrequency.YEARLY -> {
            item {
                Group("In these months") {
                    MultiChoices(
                        Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                        selected = s.reminderMonths.map { it.ordinal }.toSet(),
                        perRow = 4,
                    ) { i ->
                        update {
                            val month = Month.entries[i]
                            val months = if (month in it.reminderMonths) it.reminderMonths - month else it.reminderMonths + month
                            if (months.isEmpty()) it else it.copy(reminderMonths = months) // keep at least one month
                        }
                    }
                    Note("Once in each month you pick. Tap a month to add or remove it.")
                }
            }
            item {
                Group("On") {
                    Choices(dayLabels, selected = s.reminderDay.ordinal, perRow = 4) { i ->
                        update { it.copy(reminderDay = DayOfWeek.entries[i]) }
                    }
                    Note("The first ${s.reminderDay.getDisplayName(TextStyle.FULL, Locale.getDefault())} of each month you pick.")
                }
            }
        }
    }
    item { ReminderWindow(s, update) }
    item {
        Text(
            "Next reminder: " + (s.nextReminderAt?.let { dateTime(it) } ?: "not planned") +
                if (s.demoMode && s.nextReminderAt != null) " (pretend clock)" else "",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    item {
        Note(
            "On Samsung and OnePlus phones, set Settings → Apps → Reach Out → Battery to Unrestricted " +
                "so reminders arrive on time."
        )
    }
}

private fun LazyListScope.messagesPage(
    s: Settings,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    item { Note("Your chosen message pre-fills Text and Share. Tap one to choose it.") }
    item {
        Group("Messages") {
            Settings.BUILT_IN_MESSAGES.forEach { m -> SelectRow(Settings.messageLabel(m), s.message == m) { onSelect(m) } }
        }
    }
    item {
        Group("Your own") {
            if (s.customMessages.isEmpty()) Note("Nothing saved yet.")
            s.customMessages.forEachIndexed { i, m ->
                SelectRow(m, s.message == m) { onSelect(m) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onEdit(i) }) { Text("Edit") }
                    TextButton(onClick = { onDelete(i) }) { Text("Delete") }
                }
            }
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("+ Write your own") }
        }
    }
}

private fun LazyListScope.displayPage(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    item {
        Group("Theme") {
            Choices(ThemeMode.entries.map { it.label }, selected = s.themeMode.ordinal, perRow = 3) { i ->
                update { it.copy(themeMode = ThemeMode.entries[i]) }
            }
        }
    }
    item {
        Group("Color palette") {
            ColorPalette.entries.forEach { p ->
                PaletteRow(p, selected = s.palette == p) { update { it.copy(palette = p) } }
            }
        }
    }
    item {
        Group("Text size") {
            Choices(TEXT_SCALE_LABELS, selected = nearestIndex(TEXT_SCALES, s.textScale), perRow = 3) { i ->
                update { it.copy(textScale = TEXT_SCALES[i]) }
            }
        }
    }
    item {
        Group("Motion and sound") {
            ToggleRow("Reduce motion", "The wheel fades instead of spinning", s.reduceMotion) { on ->
                update { it.copy(reduceMotion = on) }
            }
            ToggleRow("Spin sound", "Soft ticks as names pass", s.spinSound) { on -> update { it.copy(spinSound = on) } }
            ToggleRow("Spin vibration", "Light taps as names pass", s.spinVibration) { on ->
                update { it.copy(spinVibration = on) }
            }
        }
    }
}

private fun LazyListScope.backupPage(
    includePhotos: Boolean,
    onIncludePhotos: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
) {
    item {
        Group("Back up") {
            Note(
                "Saves your list, passed people, cooldowns, this week's wheel, messages and settings " +
                    "into one file. You choose where it goes, such as Downloads or Google Drive."
            )
            ToggleRow("Include wheel photos", "Makes the file bigger", includePhotos, onIncludePhotos)
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save backup file") }
        }
    }
    item {
        Group("Restore") {
            Note("Replaces what's in the app now with a backup file. Use it on a new phone or after reinstalling.")
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Restore from file") }
        }
    }
    item {
        Note(
            "Backups include the names and phone numbers of people on your list, so keep the file somewhere private. " +
                "Your phone's contacts are never changed. Demo data isn't included."
        )
    }
}

private fun LazyListScope.demoPage(
    s: Settings,
    now: Long,
    onDemo: (Boolean) -> Unit,
    onTravel: (Long) -> Unit,
    onNextBatch: () -> Unit,
    onReset: () -> Unit,
    onTestReminder: () -> Unit,
    onOpenTestTools: () -> Unit,
) {
    item {
        Group {
            ToggleRow(
                "Demo mode",
                "20 fake contacts, kept separate from your real ones and deleted when you turn this off",
                s.demoMode,
                onDemo,
            )
        }
    }
    if (s.demoMode) {
        item {
            Group("Time travel") {
                DemoClock(now, onTravel)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onTravel(Schedule.DAY_MS) }, modifier = Modifier.weight(1f)) { Text("+1 day") }
                    OutlinedButton(onClick = { onTravel(7 * Schedule.DAY_MS) }, modifier = Modifier.weight(1f)) { Text("+1 week") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onTravel(30 * Schedule.DAY_MS) }, modifier = Modifier.weight(1f)) { Text("+1 month") }
                    OutlinedButton(onClick = onNextBatch, modifier = Modifier.weight(1f)) { Text("Next batch day") }
                }
                TextButton(onClick = onReset) { Text("Reset demo") }
            }
        }
        item {
            Group("Reminders in demo mode") {
                Note(
                    "Reminders follow the pretend clock. Jumping past a planned reminder delivers it right away. " +
                        "Reminders only come on the days and hours set in Settings → Reminders, and only once " +
                        "you've kept at least one person."
                )
                OutlinedButton(onClick = onTestReminder, modifier = Modifier.fillMaxWidth()) { Text("Send a test reminder now") }
            }
        }
    }
    item {
        OutlinedButton(onClick = onOpenTestTools, modifier = Modifier.fillMaxWidth()) { Text("Open test tools") }
    }
}

// =====================================================================
// Building blocks
// =====================================================================

@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A row of buttons where the chosen one is filled in. */
@Composable
private fun Choices(options: List<String>, selected: Int, perRow: Int, onSelect: (Int) -> Unit) {
    // Bigger text = fewer buttons per row, so labels never get cut off.
    val fontScale = LocalDensity.current.fontScale
    val columns = when {
        fontScale >= 1.25f -> minOf(perRow, 2)
        fontScale >= 1.1f -> minOf(perRow, 3)
        else -> perRow
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { (i, label) ->
                    val pad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    val buttonModifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                    if (i == selected) {
                        Button(onClick = { onSelect(i) }, modifier = buttonModifier, contentPadding = pad) {
                            Text(label, textAlign = TextAlign.Center)
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(i) }, modifier = buttonModifier, contentPadding = pad) {
                            Text(label, textAlign = TextAlign.Center)
                        }
                    }
                }
                // Keep buttons the same width on a short last row.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Like [Choices], but any number can be picked. Picked ones are filled in and ticked. */
@Composable
private fun MultiChoices(options: List<String>, selected: Set<Int>, perRow: Int, onToggle: (Int) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val columns = when {
        fontScale >= 1.25f -> minOf(perRow, 2)
        fontScale >= 1.1f -> minOf(perRow, 3)
        else -> perRow
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { (i, label) ->
                    val pad = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    val buttonModifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { stateDescription = if (i in selected) "Selected" else "Not selected" }
                    if (i in selected) {
                        Button(onClick = { onToggle(i) }, modifier = buttonModifier, contentPadding = pad) {
                            Text("✓ $label", textAlign = TextAlign.Center)
                        }
                    } else {
                        OutlinedButton(onClick = { onToggle(i) }, modifier = buttonModifier, contentPadding = pad) {
                            Text(label, textAlign = TextAlign.Center)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Stepper(
    title: String,
    value: String,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onMinus,
                enabled = canMinus,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp),
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onPlus,
                enabled = canPlus,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun CheckRow(name: String, detail: String?, checked: Boolean, onToggle: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PersonRow(name: String, detail: String?, action: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/** One palette choice: three color dots, the name and a short description. */
@Composable
private fun PaletteRow(palette: ColorPalette, selected: Boolean, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Use the ${palette.label} palette", onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            paletteSwatches(palette, dark).forEach { c ->
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(palette.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                palette.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhotoThumb(name: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { WheelPhotos.load(context, name)?.asImageBitmap() }
    }
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        bitmap?.let {
            Image(it, contentDescription = "Wheel photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Button(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        ) { Text("✕", color = MaterialTheme.colorScheme.onSurface) }
    }
}

// =====================================================================
// Helpers
// =====================================================================

private fun hasContactsPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.getDefault())

/** "Only between": two time fields. Tap one to type a new time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderWindow(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    // null = no dialog, true = editing From, false = editing Until
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val stacked = LocalDensity.current.fontScale >= 1.25f

    Group("Only between") {
        if (stacked) {
            TimeField("From", clock(s.windowStartMin), Modifier.fillMaxWidth()) { editing = true }
            TimeField("Until", clock(s.windowEndMin), Modifier.fillMaxWidth()) { editing = false }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("From", clock(s.windowStartMin), Modifier.weight(1f)) { editing = true }
                TimeField("Until", clock(s.windowEndMin), Modifier.weight(1f)) { editing = false }
            }
        }
        Note("Tap a time to change it. Reminders and 1-hour snoozes only arrive inside this window.")
    }

    val isStart = editing ?: return
    key(isStart) {
        val current = if (isStart) s.windowStartMin else s.windowEndMin
        val state = rememberTimePickerState(initialHour = current / 60, initialMinute = current % 60, is24Hour = false)
        val picked = state.hour * 60 + state.minute
        val problem = if (isStart) {
            if (picked > s.windowEndMin - 60) "Choose a time at least 1 hour before ${clock(s.windowEndMin)}." else null
        } else {
            if (picked < s.windowStartMin + 60) "Choose a time at least 1 hour after ${clock(s.windowStartMin)}." else null
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isStart) "Reminders from" else "Reminders until") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeInput(state = state)
                    if (problem != null) {
                        Text(
                            problem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = problem == null,
                    onClick = {
                        update { if (isStart) it.copy(windowStartMin = picked) else it.copy(windowEndMin = picked) }
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

/** Time travel: the pretend date, plus an editable pretend time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoClock(now: Long, onTravel: (Long) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val here = Instant.ofEpochMilli(now).atZone(zone)
    val minuteOfDay = here.hour * 60 + here.minute

    Text(
        "Pretend it's " + here.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
        fontWeight = FontWeight.SemiBold,
    )
    TimeField("Pretend time", clock(minuteOfDay), Modifier.fillMaxWidth()) { editing = true }
    Note("Tap the time to type a new hour and minute on the same day. Earlier times work too.")

    if (editing) {
        val state = rememberTimePickerState(initialHour = here.hour, initialMinute = here.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Pretend time") },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val target = here.toLocalDate().atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    onTravel(target - now)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

/** Monthly: the date each month, as a field. Tap it to type a day from 1 to 31. */
@Composable
private fun MonthlyDate(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val day = s.reminderDayOfMonth.takeIf { it in 1..31 } ?: LocalDate.now().dayOfMonth
    Group("On this date") {
        TimeField("Every month on the", ordinal(day), Modifier.fillMaxWidth()) { editing = true }
        Note(
            if (day > 28) "In months without a ${ordinal(day)}, the reminder comes on the last day of the month."
            else "Tap the date to change it."
        )
    }
    if (editing) {
        var text by remember { mutableStateOf(day.toString()) }
        val value = text.trim().toIntOrNull()
        val valid = value != null && value in 1..31
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Day of the month") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { new -> text = new.filter { it.isDigit() }.take(2) },
                        label = { Text("1 to 31") },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (valid) "Reminds you on the ${ordinal(value!!)} of every month."
                        else "Type a day from 1 to 31.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        if (value != null) {
                            update { it.copy(reminderDayOfMonth = value) }
                        }
                        editing = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

/** 1 → "1st", 2 → "2nd", 11 → "11th", 22 → "22nd". */
private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

/** Looks like a form field; tapping it opens the time editor. */
@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClickLabel = "Change the $label time", role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text("✎", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun clock(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun dateTime(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE MMM d, h:mm a"))

private fun shortDate(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

private fun breakLabel(days: Int): String = when {
    days < 30 -> if (days == 7) "1 week" else "${days / 7} weeks"
    days < 365 -> (days / 30).let { if (it == 1) "1 month" else "$it months" }
    else -> "1 year"
}

private fun breakRange(minDays: Int, maxDays: Int): String =
    if (minDays == maxDays) breakLabel(minDays) else "${breakLabel(minDays)} – ${breakLabel(maxDays)}"

private fun nearestIndex(options: List<Int>, value: Int): Int =
    options.indices.minBy { kotlin.math.abs(options[it] - value) }

private fun nearestIndex(options: List<Float>, value: Float): Int =
    options.indices.minBy { kotlin.math.abs(options[it] - value) }
