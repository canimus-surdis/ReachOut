package com.canimussurdis.reachout.data

import android.content.Context
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
            return all.filter { it.id !in state.records }.shuffled(random)
        }
    }

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
    }

    internal fun dataFile(name: String) = file(name)

    fun accept(contact: Contact) = synchronized(lock) {
        state.records[contact.id] = ContactRecord(contact, Status.ACCEPTED, decidedAt = now())
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
        save()
    }

    /** Settings → Contacts: take someone off your accepted list. They go back into the swipe deck. */
    fun removeAccepted(id: String) = synchronized(lock) {
        takeOffWheel(id, reached = false)
        state.records.remove(id)
        save()
    }

    /** Settings → Contacts: bring someone back from the Rejected list into the swipe deck. */
    fun restoreRejected(id: String) = synchronized(lock) {
        state.records.remove(id)
        save()
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
            save()
        }
    }

    /** The person waiting in the result box, if any (and still on your list). */
    fun currentPick(): Contact? = synchronized(lock) {
        val id = state.currentPick ?: return@synchronized null
        val r = state.records[id]
        if (r == null || r.status != Status.ACCEPTED) {
            state.currentPick = null
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
            spareForWheel = eligibleIds().count { id -> batch.slots.none { it.contactId == id } },
            customBreaks = records.filter { it.customMinDays != null && it.customMaxDays != null }
                .associate { it.contact.id to (it.customMinDays!! to it.customMaxDays!!) },
        )
    }

    /** Call, Text or Share was tapped. The app will ask "Did you connect?" next time it opens. */
    fun markReachedOut(id: String) = synchronized(lock) {
        val r = state.records[id] ?: return@synchronized
        if (r.pendingCheckAt == null) {
            state.records[id] = r.copy(pendingCheckAt = now())
        }
        if (state.currentPick == id) state.currentPick = null
        save()
    }

    /** The answer to "Did you connect with …?". */
    fun answerCheck(id: String, connected: Boolean) = synchronized(lock) {
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
            // No cooldown and no waiting: someone else takes the spot, and they're back in the pool right away.
            if (settings.notYetMode == NotYetMode.NEXT_WEEK) takeOffWheel(id, reached = false, retire = false)
        }
        save()
    }

    /** Snooze → Skip: off this week's wheel, no cooldown, back in the pool next week. */
    fun skip(id: String) = synchronized(lock) {
        takeOffWheel(id, reached = false)
        if (state.currentPick == id) state.currentPick = null
        save()
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

    fun setDemoMode(on: Boolean) = synchronized(lock) {
        if (on == settings.demoMode) return@synchronized
        file(DEMO_STATE_FILE).delete() // Demo always starts fresh and leaves nothing behind.
        updateSettings(settings.copy(demoMode = on, demoTimeOffsetMs = 0L))
        state = Storage.readState(stateFile())
    }

    fun timeTravel(ms: Long) = synchronized(lock) {
        if (!settings.demoMode) return@synchronized
        updateSettings(settings.copy(demoTimeOffsetMs = settings.demoTimeOffsetMs + ms))
    }

    /** Jump to one minute past midnight on the next batch day. */
    fun jumpToNextBatchDay() = synchronized(lock) {
        if (!settings.demoMode) return@synchronized
        val now = now()
        val next = Schedule.nextBatchStart(now, settings.batchDay)
        updateSettings(settings.copy(demoTimeOffsetMs = settings.demoTimeOffsetMs + (next - now) + 60_000L))
    }

    fun resetDemo() = synchronized(lock) {
        if (!settings.demoMode) return@synchronized
        file(DEMO_STATE_FILE).delete()
        updateSettings(settings.copy(demoTimeOffsetMs = 0L))
        state = AppState()
    }

    // ---------------- Internals ----------------

    private fun source(): ContactSource =
        if (settings.demoMode) DemoContactsSource(context) else PhoneContactsSource(context)

    private fun file(name: String) = File(context.filesDir, name)

    private fun stateFile() = file(if (settings.demoMode) DEMO_STATE_FILE else STATE_FILE)

    private fun save() = Storage.writeState(stateFile(), state)

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
            val picks = eligibleIds().shuffled(random).take(settings.wheelSize)
            state.batch = WheelBatch(now, picks.map { Slot(it, SlotState.ACTIVE) }, emptySet())
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
        val picks = fillCandidates(onWheel, b.retired).take(free)
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
    private fun fillCandidates(exclude: Set<String>, retired: Set<String>): List<String> {
        val pool = eligibleIds().filter { it !in exclude }
        val fresh = pool.filter { it !in retired }.shuffled(random)
        val setAside = pool.filter { it in retired }.shuffled(random)
        return fresh + setAside
    }

    /**
     * Take someone off this week's wheel.
     * Refill mode: a random eligible person takes the same spot (or the spot goes away if nobody's left).
     * Reveal mode: the spot stays, uncovered if you connected, covered if not.
     */
    /**
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
                val replacement = fillCandidates(onWheel + id, retired).firstOrNull()
                if (replacement != null) slots[index] = Slot(replacement, SlotState.ACTIVE)
                else slots.removeAt(index)
            }
        }
        state.batch = b.copy(slots = slots, retired = retired)
    }
}
