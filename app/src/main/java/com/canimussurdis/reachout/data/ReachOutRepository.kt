package com.canimussurdis.reachout.data

import android.content.Context
import com.canimussurdis.reachout.reminders.Reminders
import com.canimussurdis.reachout.widget.ReachOutWidgets
import java.io.File
import java.time.DayOfWeek
import kotlin.random.Random

/**
 * The app's brain: who's been swiped, who's on this week's wheel, cooldowns and
 * the "Did you connect?" checks. Everything is saved to the app's private files.
 * Demo mode uses a separate file and a fake contact list, so it never touches real data.
 */
class ReachOutRepository private constructor(private val context: Context) {

    companion object {
        internal const val SETTINGS_FILE = "settings.json"
        internal const val STATE_FILE = "state.json"
        internal const val DEMO_STATE_FILE = "state_demo.json"

        @Volatile
        private var instance: ReachOutRepository? = null

        fun get(context: Context): ReachOutRepository =
            instance ?: synchronized(this) {
                instance ?: ReachOutRepository(context.applicationContext).also { instance = it }
            }
    }

    private val lock = Any()
    private val random: Random = Random.Default

    var settings: Settings = Storage.readSettings(file(SETTINGS_FILE))
        private set

    private var state: AppState = Storage.readState(stateFile())

    // ---------------- Clock ----------------

    /** Real time, plus any demo "time travel". */
    fun now(): Long = System.currentTimeMillis() + if (settings.demoMode) settings.demoTimeOffsetMs else 0L

    // ---------------- Swipe deck ----------------

    /**
     * Contacts you haven't swiped on yet, shuffled. Also refreshes saved names,
     * photos and numbers for people you've already swiped on.
     * Call off the main thread. Throws SecurityException without contacts permission.
     */
    fun loadSwipeDeck(): List<Contact> {
        val all = source().load()
        synchronized(lock) {
            var changed = matchRestoredContacts(all)
            for (c in all) {
                val r = state.records[c.id] ?: continue
                if (r.contact != c) {
                    state.records[c.id] = r.copy(contact = c)
                    changed = true
                }
            }
            if (changed) save()
            return undecidedLocked(all).shuffled(random)
        }
    }

    /**
     * The same person can be in your contacts more than once (for example from Google and the SIM,
     * or a .vcf import), each with its own id. Treat the same name with a shared phone number as one
     * person, so passing on (or keeping, or ignoring) one copy covers the others too.
     */
    private fun personKeys(c: Contact): List<String> {
        val name = c.name.trim().lowercase()
        val numbers = c.phones.map { p -> p.number.filter { it.isDigit() }.takeLast(10) }.filter { it.isNotEmpty() }
        return if (numbers.isEmpty()) listOf("$name|") else numbers.map { "$name|$it" }
    }

    /** People nobody has decided on yet (by id, or as a duplicate of someone decided), one card per person. */
    private fun undecidedLocked(contacts: List<Contact>): List<Contact> {
        val decided = HashSet<String>()
        state.records.values.forEach { r -> decided += personKeys(r.contact) }
        val seen = HashSet<String>()
        return contacts.filter { c ->
            if (c.id in state.records) return@filter false
            val keys = personKeys(c)
            if (keys.any { it in decided || it in seen }) return@filter false
            seen += keys
            true
        }
    }

    /**
     * Removed, restored or un-ignored: forget the decision for this person, including any duplicate
     * copies with the same status, and put them back in line to be swiped on. Call inside the lock.
     */
    private fun releasePerson(id: String) {
        val record = state.records.remove(id) ?: return
        val keys = personKeys(record.contact).toSet()
        state.records.values
            .filter { it.status == record.status && personKeys(it.contact).any { k -> k in keys } }
            .map { it.contact.id }
            .forEach { state.records.remove(it) }
        state.pendingSwipe[id] = record.contact
    }

    /** See [undecidedLocked]. */
    fun undecided(contacts: List<Contact>): List<Contact> = synchronized(lock) { undecidedLocked(contacts) }

    /**
     * After restoring a backup on a new phone, contacts have new ids. Match saved people to phone
     * contacts with the same name and a shared phone number, so nobody shows up twice.
     */
    private fun matchRestoredContacts(phone: List<Contact>): Boolean {
        if (settings.demoMode) return false
        val phoneIds = phone.map { it.id }.toSet()
        val orphans = state.records.values.filter { it.contact.id !in phoneIds }.toMutableList()
        if (orphans.isEmpty()) return false
        fun digits(n: String) = n.filter { it.isDigit() }.takeLast(10)
        var changed = false
        for (c in phone) {
            if (c.id in state.records) continue
            val numbers = c.phones.map { digits(it.number) }.toSet()
            val match = orphans.firstOrNull { o ->
                o.contact.name.equals(c.name, ignoreCase = true) &&
                    (o.contact.phones.isEmpty() && numbers.isEmpty() ||
                        o.contact.phones.any { digits(it.number) in numbers })
            } ?: continue
            orphans.remove(match)
            val oldId = match.contact.id
            state.records.remove(oldId)
            state.records[c.id] = match.copy(contact = c)
            state.batch?.let { b ->
                state.batch = b.copy(
                    slots = b.slots.map { if (it.contactId == oldId) it.copy(contactId = c.id) else it },
                    retired = b.retired.map { if (it == oldId) c.id else it }.toSet(),
                    skipped = b.skipped.map { if (it == oldId) c.id else it }.toSet(),
                )
            }
            if (state.currentPick == oldId) state.currentPick = c.id
            changed = true
        }
        return changed
    }

    /** Re-read everything from disk (after restoring a backup). */
    fun reloadFromDisk() = synchronized(lock) {
        settings = Storage.readSettings(file(SETTINGS_FILE))
        state = Storage.readState(stateFile())
        updateWidgets()
    }

    internal fun dataFile(name: String) = file(name)

    /** Keep someone. [frequency] sets how long they rest after you connect (Surprise me = the usual break). */
    fun accept(contact: Contact, frequency: KeepFrequency = KeepFrequency.SURPRISE) = synchronized(lock) {
        state.records[contact.id] = ContactRecord(
            contact,
            Status.ACCEPTED,
            decidedAt = now(),
            customMinDays = frequency.minDays,
            customMaxDays = frequency.maxDays,
        )
        state.reswipe -= contact.id
        state.pendingSwipe.remove(contact.id)
        if (settings.reachedMode == ReachedMode.REFILL) topUpWheel()
        save()
    }

    /**
     * Add many people at once (Import contacts). Skips anyone already on your list or passed.
     * Returns how many were added.
     */
    fun acceptMany(contacts: List<Contact>): Int = synchronized(lock) {
        val now = now()
        var added = 0
        for (c in contacts) {
            if (c.id in state.records) continue
            state.records[c.id] = ContactRecord(c, Status.ACCEPTED, decidedAt = now)
            state.reswipe -= c.id
            state.pendingSwipe.remove(c.id)
            added++
        }
        if (added > 0) {
            if (settings.reachedMode == ReachedMode.REFILL) topUpWheel()
            save()
        }
        added
    }

    /** Every contact from the current source (phone or demo), or empty without permission. Call off the main thread. */
    fun loadAllContacts(): List<Contact> = runCatching { source().load() }.getOrDefault(emptyList())

    /**
     * Give these people their own break length after you connect (null, null = back to the default).
     * Applies from the next time you connect; a break that's already running isn't changed.
     */
    fun setCustomBreak(ids: Collection<String>, minDays: Int?, maxDays: Int?) = synchronized(lock) {
        val min = minDays
        val max = if (min != null && maxDays != null) maxOf(maxDays, min) else maxDays
        for (id in ids) {
            val r = state.records[id] ?: continue
            state.records[id] = r.copy(customMinDays = min, customMaxDays = max)
        }
        save()
    }

    /** Ids already on your list or passed. */
    fun knownIds(): Set<String> = synchronized(lock) { state.records.keys.toSet() }

    fun reject(contact: Contact) = synchronized(lock) {
        state.records[contact.id] = ContactRecord(contact, Status.REJECTED, decidedAt = now())
        state.reswipe -= contact.id
        state.pendingSwipe.remove(contact.id)
        save()
    }

    /**
     * Settings → Contacts: take someone off your accepted list. They go back into the swipe deck,
     * and you'll swipe on them again the next time the app opens.
     */
    fun removeAccepted(id: String) {
        synchronized(lock) {
            takeOffWheel(id, reached = false)
            releasePerson(id)
            save()
        }
        Reminders.clearFor(context, listOf(id))
    }

    /** Settings → Contacts → Remove several: like [removeAccepted], for many people at once. */
    fun removeAcceptedMany(ids: Collection<String>) {
        synchronized(lock) {
            for (id in ids) {
                takeOffWheel(id, reached = false)
                releasePerson(id)
            }
            save()
        }
        Reminders.clearFor(context, ids)
    }

    /** Settings → Contacts: bring someone back from the Passed list. Swiped on again next time the app opens. */
    fun restoreRejected(id: String) = synchronized(lock) {
        releasePerson(id)
        save()
    }

    /**
     * Settings → Contacts → Ignore: these people never show up as swipe cards or in imports until
     * un-ignored. Anyone on your list comes off it (and off the wheel).
     */
    fun ignore(contacts: Collection<Contact>) {
        synchronized(lock) {
            for (c in contacts) {
                takeOffWheel(c.id, reached = false)
                if (state.currentPick == c.id) {
                    state.currentPick = null
                    state.pickSnoozed = false
                }
                state.reswipe -= c.id
                state.pendingSwipe.remove(c.id)
                state.records[c.id] = ContactRecord(c, Status.IGNORED, decidedAt = now())
            }
            save()
        }
        Reminders.clearFor(context, contacts.map { it.id })
    }

    /** Un-ignore: back to the swipe deck, and swiped on again the next time the app opens. */
    fun unignore(id: String) = synchronized(lock) {
        if (state.records[id]?.status == Status.IGNORED) {
            releasePerson(id)
            save()
        }
    }

    /** What each saved person is (on your list, passed or ignored), by id. */
    fun statuses(): Map<String, Status> = synchronized(lock) { state.records.mapValues { it.value.status } }

    /** Import contacts: these people wait as swipe cards (shown now, and each time the app opens until decided). */
    fun queueForSwipe(contacts: Collection<Contact>) = synchronized(lock) {
        var changed = false
        for (c in contacts) {
            if (c.id in state.records || c.id in state.pendingSwipe) continue
            state.pendingSwipe[c.id] = c
            changed = true
        }
        if (changed) save()
    }

    /** Everyone waiting to be swiped on, not yet decided. */
    fun pendingSwipe(): List<Contact> = synchronized(lock) {
        undecidedLocked(state.pendingSwipe.values.toList())
    }

    /** People waiting to be swiped on again (older saved data, by id only). */
    fun reswipeIds(): Set<String> = synchronized(lock) { state.reswipe.toSet() }

    /** Forget re-swipes for people who can't be shown (e.g. no longer in your phone's contacts). */
    fun dropReswipe(ids: Collection<String>) = synchronized(lock) {
        if (state.reswipe.removeAll(ids.toSet())) save()
    }

    /** A saved contact by id (for reminder notifications). */
    fun contactById(id: String): Contact? = synchronized(lock) { state.records[id]?.contact }

    fun hasAcceptedContacts(): Boolean = synchronized(lock) {
        state.records.values.any { it.status == Status.ACCEPTED }
    }

    // ---------------- The wheel's pick ----------------

    /** Remember who the wheel picked, so the box stays until you act on it. */
    fun setCurrentPick(id: String?) = synchronized(lock) {
        if (state.currentPick != id) {
            state.currentPick = id
            state.pickSnoozed = false // a new pick gets its own snooze
            save()
        }
    }

    /** Snooze uses up the pick's one snooze; the person stays picked until you reach out. */
    fun snoozeCurrentPick() = synchronized(lock) {
        if (state.currentPick != null && !state.pickSnoozed) {
            state.pickSnoozed = true
            save()
        }
    }

    /** Whether the current pick has already been snoozed once. */
    fun isPickSnoozed(): Boolean = synchronized(lock) { state.currentPick != null && state.pickSnoozed }

    /** What the home screen widgets show: the pick (if any), and whether anyone is on your list. */
    data class WidgetInfo(val pick: Contact?, val hasPeople: Boolean)

    fun widgetInfo(): WidgetInfo = synchronized(lock) {
        val pick = state.currentPick?.let { state.records[it] }?.takeIf { it.status == Status.ACCEPTED }?.contact
        WidgetInfo(pick, state.records.values.any { it.status == Status.ACCEPTED })
    }

    /** What the widgets showed last time, so they're only redrawn when something they show changes. */
    private var widgetKey: String? = null

    private fun updateWidgets() {
        val info = widgetInfo()
        val key = "${info.pick?.id}|${info.pick?.name}|${info.pick?.mainNumber}|${info.hasPeople}"
        if (key == widgetKey) return
        widgetKey = key
        // A widget problem must never stop your data from saving.
        runCatching { ReachOutWidgets.refresh(context) }
    }

    /** The person waiting in the result box, if any (and still on your list). */
    fun currentPick(): Contact? = synchronized(lock) {
        val id = state.currentPick ?: return@synchronized null
        val r = state.records[id]
        if (r == null || r.status != Status.ACCEPTED) {
            state.currentPick = null
            state.pickSnoozed = false
            save()
            null
        } else {
            r.contact
        }
    }

    // ---------------- Wheel ----------------

    /** Everything a screen needs, in one consistent read. Draws a new weekly batch if it's due. */
    fun snapshot(): Snapshot = synchronized(lock) {
        ensureCurrentBatch()
        val now = now()
        val records = state.records.values
        val batch = state.batch ?: WheelBatch(now, emptyList(), emptySet())
        Snapshot(
            settings = settings,
            now = now,
            pendingChecks = records.filter { it.pendingCheckAt != null }
                .sortedBy { it.pendingCheckAt }
                .map { it.contact },
            wheel = WheelView(
                drawnAt = batch.drawnAt,
                slots = batch.slots.mapNotNull { s ->
                    state.records[s.contactId]?.let { WheelSlotView(it.contact, s.state) }
                },
            ),
            cooldowns = records.filter { (it.cooldownUntil ?: 0L) > now }
                .sortedBy { it.cooldownUntil }
                .map { CooldownView(it.contact, it.cooldownUntil!!) },
            accepted = records.filter { it.status == Status.ACCEPTED }
                .map { it.contact }.sortedBy { it.name.lowercase() },
            rejected = records.filter { it.status == Status.REJECTED }
                .map { it.contact }.sortedBy { it.name.lowercase() },
            ignored = records.filter { it.status == Status.IGNORED }
                .map { it.contact }.sortedBy { it.name.lowercase() },
            spareForWheel = eligibleIds().count { id -> id !in batch.skipped && batch.slots.none { it.contactId == id } },
            customBreaks = records.filter { it.customMinDays != null && it.customMaxDays != null }
                .associate { it.contact.id to (it.customMinDays!! to it.customMaxDays!!) },
        )
    }

    /** Call, Text or Share was tapped. The app will ask "Did you connect?" next time it opens. */
    fun markReachedOut(id: String) {
        synchronized(lock) {
            val r = state.records[id] ?: return@synchronized
            if (r.pendingCheckAt == null) {
                state.records[id] = r.copy(pendingCheckAt = now())
            }
            if (state.currentPick == id) {
                state.currentPick = null
                state.pickSnoozed = false
            }
            save()
        }
        Reminders.clearFor(context, listOf(id))
    }

    /** The answer to "Did you connect with …?". */
    fun answerCheck(id: String, connected: Boolean) {
        synchronized(lock) {
            val r = state.records[id] ?: return@synchronized
            val start = r.pendingCheckAt ?: now()
            if (connected) {
                // This person's own break length if one is set, otherwise the default.
                val min = (r.customMinDays ?: settings.cooldownMinDays).coerceAtLeast(0)
                val max = (r.customMaxDays ?: settings.cooldownMaxDays).coerceAtLeast(min)
                val days = random.nextInt(min, max + 1)
                state.records[id] = r.copy(pendingCheckAt = null, cooldownUntil = start + days * Schedule.DAY_MS)
                takeOffWheel(id, reached = true)
            } else {
                state.records[id] = r.copy(pendingCheckAt = null)
                // Back to pool: off this week's wheel for good (not even used to fill a gap), no cooldown,
                // and back in the pool to be drawn again from the next batch day.
                if (settings.notYetMode == NotYetMode.NEXT_WEEK) {
                    state.batch?.let { state.batch = it.copy(skipped = it.skipped + id) }
                    takeOffWheel(id, reached = false)
                }
            }
            save()
        }
        Reminders.clearFor(context, listOf(id))
    }

    /**
     * Snooze → Back to pool: off this week's wheel for good (not even used to fill a gap), no cooldown.
     * They're back in the pool and can be drawn again from the next batch day.
     */
    fun skip(id: String) {
        synchronized(lock) {
            state.batch?.let { state.batch = it.copy(skipped = it.skipped + id) }
            takeOffWheel(id, reached = false)
            if (state.currentPick == id) {
                state.currentPick = null
                state.pickSnoozed = false
            }
            save()
        }
        Reminders.clearFor(context, listOf(id))
    }

    /** Settings → Contacts: "Bring back now". */
    fun endCooldown(id: String) = synchronized(lock) {
        val r = state.records[id] ?: return@synchronized
        state.records[id] = r.copy(cooldownUntil = null)
        state.batch?.let { state.batch = it.copy(retired = it.retired - id) }
        if (settings.reachedMode == ReachedMode.REFILL) topUpWheel()
        save()
    }

    // ---------------- Settings ----------------

    fun setReachedMode(mode: ReachedMode) = synchronized(lock) {
        updateSettings(settings.copy(reachedMode = mode))
        if (mode == ReachedMode.REFILL) {
            // Uncovered/covered slices make no sense in refill mode: drop them and fill the gaps.
            state.batch?.let { b -> state.batch = b.copy(slots = b.slots.filter { it.state == SlotState.ACTIVE }) }
            topUpWheel()
            save()
        }
    }

    fun setNotYetMode(mode: NotYetMode) = synchronized(lock) {
        updateSettings(settings.copy(notYetMode = mode))
    }

    fun setBatchDay(day: DayOfWeek) = synchronized(lock) {
        updateSettings(settings.copy(batchDay = day))
    }

    /** Small display/sound settings: pass a change, e.g. `changeSettings { it.copy(spinSound = false) }`. */
    fun changeSettings(change: (Settings) -> Settings) = synchronized(lock) {
        updateSettings(change(settings))
    }

    // ---------------- Demo mode ----------------

    fun setDemoMode(on: Boolean) {
        synchronized(lock) {
            if (on == settings.demoMode) return
            file(DEMO_STATE_FILE).delete() // Demo always starts fresh and leaves nothing behind.
            // Reminder times belong to one clock (real or pretend), so start them over.
            updateSettings(settings.copy(demoMode = on, demoTimeOffsetMs = 0L).withFreshReminders())
            state = Storage.readState(stateFile())
            updateWidgets()
        }
        Reminders.rescheduleAll(context)
    }

    fun timeTravel(ms: Long) {
        synchronized(lock) {
            if (!settings.demoMode) return
            updateSettings(settings.copy(demoTimeOffsetMs = settings.demoTimeOffsetMs + ms))
        }
        Reminders.clockChanged(context)
    }

    /** Jump to one minute past midnight on the next batch day. */
    fun jumpToNextBatchDay() {
        synchronized(lock) {
            if (!settings.demoMode) return
            val now = now()
            val next = Schedule.nextBatchStart(now, settings.batchDay)
            updateSettings(settings.copy(demoTimeOffsetMs = settings.demoTimeOffsetMs + (next - now) + 60_000L))
        }
        Reminders.clockChanged(context)
    }

    fun resetDemo() {
        synchronized(lock) {
            if (!settings.demoMode) return
            file(DEMO_STATE_FILE).delete()
            updateSettings(settings.copy(demoTimeOffsetMs = 0L).withFreshReminders())
            state = AppState()
            updateWidgets()
        }
        Reminders.rescheduleAll(context)
    }

    private fun Settings.withFreshReminders() = copy(lastReminderAt = null, nextReminderAt = null, snoozes = emptyMap())

    // ---------------- Internals ----------------

    private fun source(): ContactSource =
        if (settings.demoMode) DemoContactsSource(context) else PhoneContactsSource(context)

    private fun file(name: String) = File(context.filesDir, name)

    private fun stateFile() = file(if (settings.demoMode) DEMO_STATE_FILE else STATE_FILE)

    private fun save() {
        Storage.writeState(stateFile(), state)
        updateWidgets()
    }

    private fun updateSettings(s: Settings) {
        settings = s
        Storage.writeSettings(file(SETTINGS_FILE), s)
    }

    private fun isEligible(r: ContactRecord, now: Long) =
        r.status == Status.ACCEPTED && (r.cooldownUntil == null || r.cooldownUntil <= now)

    private fun eligibleIds(): List<String> {
        val now = now()
        return state.records.values.filter { isEligible(it, now) }.map { it.contact.id }
    }

    /** New batch on batch day (or the very first time); otherwise keep this week's wheel. */
    private fun ensureCurrentBatch() {
        val now = now()
        val weekStart = Schedule.lastBatchStart(now, settings.batchDay)
        val b = state.batch
        if (b == null || b.drawnAt < weekStart || b.slots.isEmpty()) {
            // A wheel that emptied mid-week keeps this week's "Back to pool" people off it.
            val skipped = if (b != null && b.drawnAt >= weekStart) b.skipped else emptySet()
            val picks = eligibleIds().filter { it !in skipped }.shuffled(random).take(settings.wheelSize)
            state.batch = WheelBatch(now, picks.map { Slot(it, SlotState.ACTIVE) }, emptySet(), skipped)
            save()
        } else if (settings.reachedMode == ReachedMode.REFILL) {
            if (topUpWheel()) save()
        }
    }

    /** Refill mode: fill empty spots with random eligible people not already used this week. */
    private fun topUpWheel(): Boolean {
        val b = state.batch ?: return false
        val free = settings.wheelSize - b.slots.size
        if (free <= 0) return false
        val onWheel = b.slots.map { it.contactId }.toSet()
        val picks = fillCandidates(onWheel, b.retired, b.skipped).take(free)
        if (picks.isEmpty()) return false
        state.batch = b.copy(slots = b.slots + picks.map { Slot(it, SlotState.ACTIVE) })
        return true
    }

    /**
     * People who could fill a wheel spot, in the order to use them: first anyone not used yet this
     * week, then (only if needed to keep the wheel full) people set aside earlier this week.
     * Everyone eligible = accepted and not in cooldown. So the wheel always has 12 names when
     * 12 or more people are eligible.
     */
    private fun fillCandidates(exclude: Set<String>, retired: Set<String>, skipped: Set<String>): List<String> {
        val pool = eligibleIds().filter { it !in exclude && it !in skipped }
        val fresh = pool.filter { it !in retired }.shuffled(random)
        val setAside = pool.filter { it in retired }.shuffled(random)
        return fresh + setAside
    }

    /**
     * Take someone off this week's wheel.
     * Refill mode: a random eligible person takes the same spot (or the spot goes away if nobody's left).
     * Reveal mode: the spot stays, uncovered if you connected, covered if not.
     * [retire] = true keeps them off the wheel until the next batch day.
     * false ("Not yet") frees the spot but leaves them in the pool, so they can come back this week.
     */
    private fun takeOffWheel(id: String, reached: Boolean, retire: Boolean = true) {
        val b = state.batch ?: return
        val index = b.slots.indexOfFirst { it.contactId == id }
        if (index < 0) return
        val slots = b.slots.toMutableList()
        val retired = if (retire) b.retired + id else b.retired
        when (settings.reachedMode) {
            ReachedMode.REVEAL ->
                slots[index] = Slot(id, if (reached) SlotState.REVEALED else SlotState.COVERED)
            ReachedMode.REFILL -> {
                val onWheel = b.slots.map { it.contactId }.toSet()
                val replacement = fillCandidates(onWheel + id, retired, b.skipped).firstOrNull()
                if (replacement != null) slots[index] = Slot(replacement, SlotState.ACTIVE)
                else slots.removeAt(index)
            }
        }
        state.batch = b.copy(slots = slots, retired = retired)
    }
}
