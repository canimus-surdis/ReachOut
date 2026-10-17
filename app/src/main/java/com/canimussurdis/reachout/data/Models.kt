package com.canimussurdis.reachout.data

import java.time.DayOfWeek
import java.time.Month

/** One phone number on a contact. */
data class PhoneNumber(
    val number: String,
    val label: String,
    val isDefault: Boolean,
    val isMobile: Boolean,
)

/** A person, read from the phone (or from the demo list). The app never writes to the phone's contacts. */
data class Contact(
    val id: String,
    val name: String,
    val photoUri: String?,
    val phones: List<PhoneNumber>,
) {
    /** Default number if one is marked, otherwise the first mobile, otherwise the first number. */
    val mainNumber: String?
        get() = (phones.firstOrNull { it.isDefault }
            ?: phones.firstOrNull { it.isMobile }
            ?: phones.firstOrNull())?.number

    /** Which cartoon animal to show when there's no photo. Always the same for the same contact. */
    val animalIndex: Int
        get() = Math.floorMod(id.hashCode(), ANIMAL_COUNT)

    companion object {
        const val ANIMAL_COUNT = 20
    }
}

/** ACCEPTED = on your list, REJECTED = passed, IGNORED = hidden from swiping and imports (Settings only). */
enum class Status { ACCEPTED, REJECTED, IGNORED }

/** What the app remembers about a contact you've swiped on. Stored only in the app's own files. */
data class ContactRecord(
    val contact: Contact,
    val status: Status,
    val decidedAt: Long,
    /** While set and in the future, the contact is off the wheel. */
    val cooldownUntil: Long? = null,
    /** Set when you tap Call, Text or Share; cleared when you answer "Did you connect?". */
    val pendingCheckAt: Long? = null,
    /** This person's own break length after you connect (null = use the default in Settings → Wheel). */
    val customMinDays: Int? = null,
    val customMaxDays: Int? = null,
)

enum class SlotState {
    /** A name you can still spin to. */
    ACTIVE,
    /** Reveal mode: you connected, so this slice shows the picture. */
    REVEALED,
    /** Reveal mode: skipped or "not yet", so this slice stays covered and blank. */
    COVERED,
}

data class Slot(val contactId: String, val state: SlotState)

/** This week's wheel. */
data class WheelBatch(
    val drawnAt: Long,
    val slots: List<Slot>,
    /** Contacts taken off this week's wheel. They won't be drawn again until the next batch day. */
    val retired: Set<String>,
    /** Sent back to the pool (Snooze → Back to pool, or "Not yet"): never put back on this week's wheel. */
    val skipped: Set<String> = emptySet(),
)

enum class ReachedMode { REFILL, REVEAL }

/** NEXT_WEEK ("Back to pool"): off this week's wheel, back in the pool from the next batch day. KEEP_ON_WHEEL: stays put. */
enum class NotYetMode { NEXT_WEEK, KEEP_ON_WHEEL }

data class Settings(
    val demoMode: Boolean = false,
    /** Demo-only "time travel": added to the real clock while demo mode is on. */
    val demoTimeOffsetMs: Long = 0L,
    val batchDay: DayOfWeek = DayOfWeek.SUNDAY,
    val wheelSize: Int = 12,
    /** How the wheel looks. Both styles follow the same rules. */
    val wheelStyle: WheelStyle = WheelStyle.ROUND,
    val cooldownMinDays: Int = 7,
    val cooldownMaxDays: Int = 182,
    val reachedMode: ReachedMode = ReachedMode.REFILL,
    val notYetMode: NotYetMode = NotYetMode.NEXT_WEEK,
    /** Pre-fills Text and Share. An empty string means Blank: nothing is pre-filled. */
    val message: String = DEFAULT_MESSAGE,
    val spinSound: Boolean = true,
    val spinVibration: Boolean = true,
    val reduceMotion: Boolean = false,

    // ---- Reminders ----
    val remindersOn: Boolean = true,
    val reminderFrequency: ReminderFrequency = ReminderFrequency.DAILY,
    /** Yearly: which day of the week. */
    val reminderDay: DayOfWeek = DayOfWeek.SUNDAY,
    /** Monthly: the date each month (1–31; shorter months use their last day). 0 = not chosen yet. */
    val reminderDayOfMonth: Int = 0,
    /** Daily: the days of the week to remind on (all seven = every day). */
    val dailyDays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** Weekly and Every 2 weeks: every day picked here. */
    val reminderDays: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY),
    /** Yearly: the months to remind in (on the first reminderDay of each). */
    val reminderMonths: Set<Month> = emptySet(),
    /** Reminder window, in minutes after midnight. Default 5–9 pm. */
    val windowStartMin: Int = 17 * 60,
    val windowEndMin: Int = 21 * 60,
    /** "Different hours on some days": when on, days in [dayWindows] use their own hours. */
    val perDayWindows: Boolean = false,
    /** Day → (start, end) in minutes after midnight, for days with their own hours. Other days use the usual window. */
    val dayWindows: Map<DayOfWeek, Pair<Int, Int>> = emptyMap(),
    val lastReminderAt: Long? = null,
    val nextReminderAt: Long? = null,
    /** Contact id → when its 1-hour snooze reminder is due. */
    val snoozes: Map<String, Long> = emptyMap(),
    val askedNotificationPermission: Boolean = false,

    // ---- Display ----
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 1.0 normal, 1.15 large, 1.3 extra large. */
    val textScale: Float = 1f,
    val palette: ColorPalette = ColorPalette.TERRACOTTA,

    // ---- Messages ----
    val customMessages: List<String> = emptyList(),

    // ---- Hidden picture (reveal mode) ----
    val pictureSource: PictureSource = PictureSource.DESIGNS,
    /** File names of your own photos, stored in the app's private folder. */
    val wheelPhotos: List<String> = emptyList(),
) {
    /** The reminder hours for [day]: its own hours if it has them, otherwise the usual window. */
    fun windowFor(day: DayOfWeek): Pair<Int, Int> =
        (if (perDayWindows) dayWindows[day] else null) ?: (windowStartMin to windowEndMin)

    companion object {
        /** The Blank preset: Text and Share open with nothing typed. */
        const val BLANK_MESSAGE = ""

        /** The default is Blank. */
        const val DEFAULT_MESSAGE = BLANK_MESSAGE

        val BUILT_IN_MESSAGES = listOf(
            BLANK_MESSAGE,
            "Hey! It's been a while. How are you doing?",
            "Thinking about you",
            "Saw something that reminded me of you.",
            "Hope your week is going well!",
            "Want to catch up soon?",
        )

        /** How a message is shown in Settings. */
        fun messageLabel(message: String) = if (message.isBlank()) "Blank (no message)" else message
    }
}

enum class ThemeMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }

/** The app's color palettes (each has a light and a dark version). Colors live in ui/theme/Theme.kt. */
enum class ColorPalette(val label: String, val blurb: String) {
    TERRACOTTA("Terracotta", "Warm clay and sage"),
    NEUTRAL("Neutral", "Soft greys and charcoal"),
    OCEAN("Ocean", "Calm teal and sea glass"),
    LAVENDER("Lavender", "Soft purple and mint"),
    FOREST("Forest", "Leafy green and moss"),
    SUNSHINE("Sunshine", "Golden honey and leaf"),
}

/**
 * How often someone comes up, chosen below the card when you keep them.
 * Each choice is just that person's break length after you connect ([ContactRecord.customMinDays]
 * to [ContactRecord.customMaxDays]). SURPRISE has no range of its own: it uses the usual break in
 * Settings → Wheel.
 */
enum class KeepFrequency(val label: String, val blurb: String, val minDays: Int?, val maxDays: Int?) {
    WEEKLY("Weekly", "About every week", 7, 7),
    MONTHLY("Monthly", "About once a month", 28, 35),
    FEW_MONTHS("Few months", "Every 2 to 4 months", 61, 122),
    SURPRISE("Surprise me", "Your usual range", null, null);

    companion object {
        /** The choice a break length matches, or null if it's an exact custom length set in Settings. */
        fun of(minDays: Int?, maxDays: Int?): KeepFrequency? =
            if (minDays == null || maxDays == null) SURPRISE
            else entries.firstOrNull { it.minDays == minDays && it.maxDays == maxDays }
    }
}

/** ROUND: the classic prize wheel. BIG: a tall wheel that rolls downward, like the one on The Price is Right. */
enum class WheelStyle(val label: String) { ROUND("Round wheel"), BIG("Big wheel") }

enum class PictureSource(val label: String) { DESIGNS("Designs"), PHOTOS("My photos"), MIX("Mix") }

enum class ReminderFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    BIWEEKLY("Every 2 weeks"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
}

/** Everything the app stores (apart from settings). Demo mode keeps a separate copy. */
class AppState(
    val records: LinkedHashMap<String, ContactRecord> = linkedMapOf(),
    var batch: WheelBatch? = null,
    /** The person the wheel picked, until you Call, Text, Share or put them back in the pool. */
    var currentPick: String? = null,
    /** True once the current pick has used its one snooze. */
    var pickSnoozed: Boolean = false,
    /** People you removed (or restored from Passed): swipe on them again the next time the app opens. */
    val reswipe: LinkedHashSet<String> = linkedSetOf(),
    /**
     * People waiting to be swiped on (picked in Import contacts, removed from your list, restored or
     * un-ignored), kept with their details so file contacts work too. The app opens on their cards.
     */
    val pendingSwipe: LinkedHashMap<String, Contact> = linkedMapOf(),
)

// ---- Read-only views handed to the screens ----

data class WheelSlotView(val contact: Contact, val state: SlotState)

data class WheelView(val drawnAt: Long, val slots: List<WheelSlotView>) {
    val hasActiveNames: Boolean get() = slots.any { it.state == SlotState.ACTIVE }
}

data class CooldownView(val contact: Contact, val returnsAt: Long)

data class Snapshot(
    val settings: Settings,
    val now: Long,
    val pendingChecks: List<Contact>,
    val wheel: WheelView,
    val cooldowns: List<CooldownView>,
    val accepted: List<Contact>,
    val rejected: List<Contact>,
    /** Hidden from swiping and imports until un-ignored. */
    val ignored: List<Contact> = emptyList(),
    /** Accepted people who could still fill an empty wheel spot this week. */
    val spareForWheel: Int = 0,
    /** Contact id → their custom break (shortest days to longest days). */
    val customBreaks: Map<String, Pair<Int, Int>> = emptyMap(),
)
