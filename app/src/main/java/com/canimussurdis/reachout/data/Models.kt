package com.canimussurdis.reachout.data

import java.time.DayOfWeek

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

enum class Status { ACCEPTED, REJECTED }

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
)

enum class ReachedMode { REFILL, REVEAL }

/** NEXT_WEEK (name kept for saved settings) now means: give up the spot, but go straight back into the pool. */
enum class NotYetMode { NEXT_WEEK, KEEP_ON_WHEEL }

data class Settings(
    val demoMode: Boolean = false,
    /** Demo-only "time travel": added to the real clock while demo mode is on. */
    val demoTimeOffsetMs: Long = 0L,
    val batchDay: DayOfWeek = DayOfWeek.SUNDAY,
    val wheelSize: Int = 12,
    val cooldownMinDays: Int = 7,
    val cooldownMaxDays: Int = 182,
    val reachedMode: ReachedMode = ReachedMode.REFILL,
    val notYetMode: NotYetMode = NotYetMode.NEXT_WEEK,
    /** Pre-fills Text and Share. (Presets and custom messages arrive with Settings.) */
    val message: String = "Thinking about you",
    val spinSound: Boolean = true,
    val spinVibration: Boolean = true,
    val reduceMotion: Boolean = false,

    // ---- Reminders ----
    val remindersOn: Boolean = true,
    val reminderFrequency: ReminderFrequency = ReminderFrequency.WEEKLY,
    val reminderDay: DayOfWeek = DayOfWeek.SUNDAY,
    /** Reminder window, in minutes after midnight. Default 5–9 pm. */
    val windowStartMin: Int = 17 * 60,
    val windowEndMin: Int = 21 * 60,
    val lastReminderAt: Long? = null,
    val nextReminderAt: Long? = null,
    /** Contact id → when its 1-hour snooze reminder is due. */
    val snoozes: Map<String, Long> = emptyMap(),
    val askedNotificationPermission: Boolean = false,

    // ---- Display ----
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 1.0 normal, 1.15 large, 1.3 extra large. */
    val textScale: Float = 1f,

    // ---- Messages ----
    val customMessages: List<String> = emptyList(),

    // ---- Hidden picture (reveal mode) ----
    val pictureSource: PictureSource = PictureSource.DESIGNS,
    /** File names of your own photos, stored in the app's private folder. */
    val wheelPhotos: List<String> = emptyList(),
) {
    companion object {
        val BUILT_IN_MESSAGES = listOf(
            "Thinking about you",
            "Hey! It's been a while. How are you doing?",
            "Saw something that reminded me of you.",
            "Hope your week is going well!",
            "Want to catch up soon?",
        )
    }
}

enum class ThemeMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }

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
    /** The person the wheel picked, until you Call, Text, Share or Snooze. */
    var currentPick: String? = null,
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
    /** Accepted people who could still fill an empty wheel spot this week. */
    val spareForWheel: Int = 0,
    /** Contact id → their custom break (shortest days to longest days). */
    val customBreaks: Map<String, Pair<Int, Int>> = emptyMap(),
)
