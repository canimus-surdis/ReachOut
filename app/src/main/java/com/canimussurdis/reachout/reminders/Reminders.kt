package com.canimussurdis.reachout.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.canimussurdis.reachout.MainActivity
import com.canimussurdis.reachout.R
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.data.ReminderFrequency
import com.canimussurdis.reachout.data.Settings
import com.canimussurdis.reachout.widget.ReachOutWidgets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Reminders use Android's built-in AlarmManager (no extra libraries, no exact-alarm permission).
 * Alarms are "inexact", so Android may deliver them a few minutes late to save battery.
 *
 * In demo mode, reminders follow the pretend clock: every time stored in settings (next reminder,
 * last reminder, snoozes) is pretend time, and the real Android alarm is set at the matching real moment.
 */
object Reminders {

    const val EXTRA_OPEN = "reachout.open"
    const val OPEN_WHEEL = "wheel"

    private const val CHANNEL_ID = "reminders"
    private const val ACTION_REMINDER = "com.canimussurdis.reachout.REMINDER"
    private const val ACTION_SNOOZE = "com.canimussurdis.reachout.SNOOZE"
    /** Snooze 1 hr, tapped on a reminder. */
    private const val ACTION_SNOOZE_TAPPED = "com.canimussurdis.reachout.SNOOZE_TAPPED"
    /** A "keep it until you reach out" reminder was swiped away: put it back. */
    internal const val ACTION_DISMISSED = "com.canimussurdis.reachout.DISMISSED"
    private const val EXTRA_CONTACT = "contact"
    private const val REGULAR_ID = 1
    /** Stands in for a contact id when the "spin the wheel" reminder is snoozed (nobody is picked yet). */
    private const val WHEEL_KEY = "__wheel__"

    // ---------------- Scheduling ----------------

    /** Re-plans everything: the next regular reminder and any pending snoozes. Safe to call often. */
    fun rescheduleAll(context: Context) {
        val repo = ReachOutRepository.get(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val offset = clockOffset(repo.settings)
        val realNow = System.currentTimeMillis()
        val now = realNow + offset // the app's clock (pretend time in demo mode)

        val regular = regularIntent(context)
        val next = nextRegularReminder(now, repo.settings)
        if (next == null) {
            alarms.cancel(regular)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next - offset, regular)
        }
        repo.changeSettings { it.copy(nextReminderAt = next) }

        repo.settings.snoozes.forEach { (id, at) ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(at - offset, realNow + 60_000L), snoozeIntent(context, id))
        }
        // After a restart the phone has no notifications, so put back one that's kept until you reach out.
        restoreIfMissing(context)
    }

    /**
     * If a reminder that's kept until you reach out was posted and not dealt with, but isn't showing
     * (swiped away, phone restarted, app updated), post it again.
     */
    fun restoreIfMissing(context: Context) {
        val repo = ReachOutRepository.get(context)
        val s = repo.settings
        if (!s.stickyPosted) return
        if (!s.stickyReminders || !s.remindersOn || !repo.hasAcceptedContacts()) {
            repo.changeSettings { it.copy(stickyPosted = false) }
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val showing = runCatching { manager.activeNotifications.isNotEmpty() }.getOrDefault(true)
        if (!showing) postRegular(context)
    }

    /**
     * Demo time travel moved the clock. If it jumped past the planned reminder, deliver that reminder
     * now (as if it had arrived on time), then plan everything again from the new time.
     */
    fun clockChanged(context: Context) {
        val repo = ReachOutRepository.get(context)
        val s = repo.settings
        val planned = s.nextReminderAt
        if (s.remindersOn && planned != null && planned <= repo.now()) {
            repo.changeSettings { it.copy(lastReminderAt = planned) }
            if (repo.hasAcceptedContacts()) postRegular(context)
        }
        rescheduleAll(context)
    }

    /** "Remind me in 1 hour" — or when the reminder window next opens, if that hour falls outside it. */
    fun snoozeOneHour(context: Context, contactId: String): Long {
        val repo = ReachOutRepository.get(context)
        val at = fitIntoWindow(repo.now() + 60 * 60_000L, repo.settings)
        scheduleSnoozeAt(context, contactId, at)
        return at
    }

    /** Test tools only: a snooze reminder in about a minute, ignoring the window. */
    fun snoozeForTesting(context: Context, contactId: String) =
        scheduleSnoozeAt(context, contactId, ReachOutRepository.get(context).now() + 60_000L)

    private fun scheduleSnoozeAt(context: Context, contactId: String, at: Long) {
        val repo = ReachOutRepository.get(context)
        repo.changeSettings { it.copy(snoozes = it.snoozes + (contactId to at)) }
        val realAt = maxOf(at - clockOffset(repo.settings), System.currentTimeMillis() + 60_000L)
        context.getSystemService(AlarmManager::class.java)
            ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, realAt, snoozeIntent(context, contactId))
    }

    /**
     * These people were dealt with or left the wheel (reached out, Not yet, Back to pool, removed, ignored):
     * cancel their snooze reminders and clear their notifications, so nothing nudges you about them
     * until they're picked on the wheel again.
     */
    fun clearFor(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val repo = ReachOutRepository.get(context)
        // Reaching out also ends a snoozed "spin the wheel" reminder.
        val gone = ids.toSet() + WHEEL_KEY
        if (repo.settings.snoozes.keys.any { it in gone }) {
            repo.changeSettings { it.copy(snoozes = it.snoozes - gone) }
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val shown = NotificationManagerCompat.from(context)
        gone.forEach { id ->
            alarms?.cancel(snoozeIntent(context, id))
            shown.cancel(snoozeRequestCode(id))
        }
        // A reminder already showing may name them, so clear it too. The next one comes as planned.
        shown.cancel(REGULAR_ID)
        if (repo.settings.stickyPosted) repo.changeSettings { it.copy(stickyPosted = false) }
    }

    /** How far the app's clock is ahead of the real one (only in demo mode, after time travel). */
    private fun clockOffset(s: Settings): Long = if (s.demoMode) s.demoTimeOffsetMs else 0L

    /**
     * When the next regular reminder should go off, or null if reminders are off.
     * Lands on a random minute inside the reminder window on the right day.
     */
    fun nextRegularReminder(nowMs: Long, s: Settings, random: Random = Random.Default): Long? {
        if (!s.remindersOn) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val last = s.lastReminderAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val days = s.reminderDays.ifEmpty { setOf(s.reminderDay) }
        val months = s.reminderMonths

        // The earliest day the next reminder may come.
        var date: LocalDate = when (s.reminderFrequency) {
            // Monthly on a date: the next time that date comes round after the last nudge.
            ReminderFrequency.MONTHLY -> if (s.reminderDayOfMonth in 1..31) last?.plusDays(1) else last?.plusMonths(1)
            ReminderFrequency.YEARLY -> if (months.isEmpty()) last?.plusYears(1) else last?.plusDays(1)
            else -> last?.plusDays(1)
        } ?: today
        if (date.isBefore(today)) date = today

        // Every 2 weeks: on-weeks alternate, counting from the week of the last reminder.
        val anchorWeek = weekStart(last ?: today)
        fun allowed(d: LocalDate): Boolean = when (s.reminderFrequency) {
            ReminderFrequency.DAILY -> s.dailyDays.isEmpty() || d.dayOfWeek in s.dailyDays
            ReminderFrequency.WEEKLY -> d.dayOfWeek in days
            ReminderFrequency.BIWEEKLY ->
                d.dayOfWeek in days && ChronoUnit.WEEKS.between(anchorWeek, weekStart(d)) % 2 == 0L
            // On the chosen date; in shorter months (like the 31st in April), on the last day.
            ReminderFrequency.MONTHLY -> if (s.reminderDayOfMonth in 1..31) {
                d.dayOfMonth == minOf(s.reminderDayOfMonth, d.lengthOfMonth())
            } else {
                d.dayOfWeek == s.reminderDay
            }
            // The first chosen weekday of each chosen month.
            ReminderFrequency.YEARLY ->
                d.dayOfWeek == s.reminderDay && (months.isEmpty() || (d.month in months && d.dayOfMonth <= 7))
        }

        repeat(800) {
            // Each day can have its own hours (Settings → Reminders → Different hours on some days).
            val (startMin, endMin) = window(s, date.dayOfWeek)
            val windowOverToday = date == today && nowMin >= endMin - 1
            if (allowed(date) && !windowOverToday) {
                val fromMin = if (date == today) maxOf(startMin, nowMin + 2) else startMin
                val minute = fromMin + random.nextInt(maxOf(1, endMin - fromMin))
                return at(date, minute.coerceAtMost(24 * 60 - 1), zone)
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Moves a time to the reminder window: unchanged if inside, else the window's next opening. */
    fun fitIntoWindow(timeMs: Long, s: Settings): Long {
        val zone = ZoneId.systemDefault()
        val t: ZonedDateTime = Instant.ofEpochMilli(timeMs).atZone(zone)
        val (startMin, endMin) = window(s, t.dayOfWeek)
        val minute = t.hour * 60 + t.minute
        return when {
            minute < startMin -> at(t.toLocalDate(), startMin, zone)
            minute >= endMin -> {
                val tomorrow = t.toLocalDate().plusDays(1)
                at(tomorrow, window(s, tomorrow.dayOfWeek).first, zone)
            }
            else -> timeMs
        }
    }

    /** The reminder hours on [day] (its own, or the usual window), kept sensible. */
    private fun window(s: Settings, day: DayOfWeek): Pair<Int, Int> {
        val (a, b) = s.windowFor(day)
        val start = a.coerceIn(0, 23 * 60)
        val end = b.coerceIn(start + 30, 24 * 60 - 1)
        return start to end
    }

    private fun at(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(zone).toInstant().toEpochMilli()

    private fun regularIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REGULAR_ID,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun snoozeIntent(context: Context, contactId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(contactId),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_CONTACT, contactId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun snoozeRequestCode(contactId: String) = 1000 + (contactId.hashCode() and 0xFFFF)

    // ---------------- Notifications ----------------

    fun notificationsAllowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Test tools: show the regular reminder right now. */
    fun showRegularNow(context: Context) = postRegular(context)

    /**
     * If a reminder is showing, post it again with what's true now: who the wheel picked, and whether
     * "Keep it until you reach out" is on. Does nothing when no reminder is showing.
     */
    fun refreshShowing(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val showing = runCatching { manager.activeNotifications.map { it.id } }.getOrDefault(emptyList())
        if (REGULAR_ID in showing) postRegular(context)
    }

    /** Takes away every reminder that's showing (Settings → Reminders turned off). */
    fun clearShowing(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
        val repo = ReachOutRepository.get(context)
        if (repo.settings.stickyPosted) repo.changeSettings { it.copy(stickyPosted = false) }
    }

    private fun postRegular(context: Context) {
        // Someone was picked and hasn't been reached yet: name them instead of the usual nudge.
        val repo = ReachOutRepository.get(context)
        val pick = repo.currentPick()
        if (pick != null) {
            postPick(context, REGULAR_ID, pick.id, pick.name)
            return
        }
        val sticky = repo.settings.stickyReminders
        post(
            context,
            id = REGULAR_ID,
            title = "Time to reach out to someone",
            text = if (sticky) "Spin the wheel to see who's up." else "Spin the wheel and say hi.",
            open = OPEN_WHEEL,
            pickId = null,
        )
    }

    private fun postSnooze(context: Context, contactId: String) {
        val name = ReachOutRepository.get(context).contactById(contactId)?.name
        if (name == null) {
            postRegular(context)
            return
        }
        postPick(context, snoozeRequestCode(contactId), contactId, name)
    }

    private fun postPick(context: Context, id: Int, contactId: String, name: String) {
        val sticky = ReachOutRepository.get(context).settings.stickyReminders
        val first = name.trim().substringBefore(' ').ifBlank { name }
        post(
            context,
            id = id,
            title = "Reach out to $name",
            text = if (sticky) "$first is your pick. Call or text to clear this." else "Tap to call, text or share.",
            open = contactId,
            pickId = contactId,
        )
    }

    /** A request code for one of a notification's buttons (k = which button). */
    private fun buttonCode(id: Int, k: Int) = 50_000 + (id and 0xFFFF) * 8 + k

    @SuppressLint("MissingPermission") // checked by notificationsAllowed() just below
    private fun post(context: Context, id: Int, title: String, text: String, open: String, pickId: String?) {
        if (!notificationsAllowed(context)) return
        val repo = ReachOutRepository.get(context)
        val sticky = repo.settings.stickyReminders
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nudges to reach out to someone"
            }
        )
        val tap = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // The lock screen gets this version: just the reminder, no buttons.
        val lockScreen = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB95C40.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB95C40.toInt()) // terracotta, to match the app
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)

        // Buttons: Call and Text for the pick (they count as reaching out and clear this), Spin when
        // nobody's picked yet, and Snooze 1 hr (once per pick, the same as in the app).
        val number = pickId?.let { repo.contactById(it)?.mainNumber }
        if (pickId != null && number != null) {
            builder.addAction(0, "Call", ReachOutWidgets.actionIntent(context, ReachOutWidgets.ACTION_CALL, pickId, buttonCode(id, 0)))
            builder.addAction(0, "Text", ReachOutWidgets.actionIntent(context, ReachOutWidgets.ACTION_TEXT, pickId, buttonCode(id, 1)))
        }
        if (pickId == null) builder.addAction(0, "Spin the wheel", tap)
        if (pickId == null || !repo.isPickSnoozed()) {
            val snooze = PendingIntent.getBroadcast(
                context,
                buttonCode(id, 2),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_SNOOZE_TAPPED)
                    .putExtra(EXTRA_CONTACT, pickId ?: WHEEL_KEY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Snooze 1 hr", snooze)
        }

        if (sticky) {
            // Stays until you reach out: Clear all skips it, it can't be swiped away on the lock screen,
            // and if it's swiped away anywhere else (Android 14 and newer allow that) it's put back.
            val putBack = PendingIntent.getBroadcast(
                context,
                buttonCode(id, 3),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_DISMISSED)
                    .apply { if (id != REGULAR_ID && pickId != null) putExtra(EXTRA_CONTACT, pickId) },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            if (!repo.settings.stickyPosted) repo.changeSettings { it.copy(stickyPosted = true) }
            builder.setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true) // putting it back doesn't make a sound again
                .setDeleteIntent(putBack)
        } else {
            builder.setAutoCancel(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission was revoked between the check and now; nothing to do.
        }
    }

    // ---------------- Receiver ----------------

    internal fun handle(context: Context, intent: Intent) {
        val repo = ReachOutRepository.get(context)
        when (intent.action) {
            ACTION_REMINDER -> {
                // An out-of-date alarm (the clock moved since it was set): just plan again.
                val planned = repo.settings.nextReminderAt
                if (planned != null && repo.now() < planned - 60_000L) {
                    rescheduleAll(context)
                    return
                }
                repo.changeSettings { it.copy(lastReminderAt = repo.now()) }
                if (repo.settings.remindersOn && repo.hasAcceptedContacts()) postRegular(context)
                rescheduleAll(context)
            }
            ACTION_SNOOZE -> {
                val id = intent.getStringExtra(EXTRA_CONTACT) ?: return
                if (id !in repo.settings.snoozes) return // cancelled, e.g. demo mode was switched
                repo.changeSettings { it.copy(snoozes = it.snoozes - id) }
                if (id == WHEEL_KEY) {
                    if (repo.settings.remindersOn && repo.hasAcceptedContacts()) postRegular(context)
                } else {
                    postSnooze(context, id)
                }
            }
            // Snooze 1 hr on the reminder itself.
            ACTION_SNOOZE_TAPPED -> {
                val id = intent.getStringExtra(EXTRA_CONTACT) ?: return
                val shown = NotificationManagerCompat.from(context)
                // Snoozed on purpose: it comes back when the snooze ends, not before.
                repo.changeSettings { it.copy(stickyPosted = false) }
                if (id == WHEEL_KEY) {
                    shown.cancel(REGULAR_ID)
                    scheduleSnoozeAt(context, WHEEL_KEY, fitIntoWindow(repo.now() + 60 * 60_000L, repo.settings))
                } else {
                    // Only for the person who's still the pick, and only once per pick.
                    if (repo.currentPick()?.id != id) return
                    if (repo.isPickSnoozed()) {
                        // Already snoozed in the app: show the reminder again without the Snooze button.
                        refreshShowing(context)
                        return
                    }
                    repo.snoozeCurrentPick()
                    shown.cancel(REGULAR_ID)
                    shown.cancel(snoozeRequestCode(id))
                    snoozeOneHour(context, id)
                }
            }
            // A "keep it until you reach out" reminder was swiped away: put it back if it's still needed.
            ACTION_DISMISSED -> {
                val s = repo.settings
                if (!s.stickyReminders || !s.remindersOn) return
                val id = intent.getStringExtra(EXTRA_CONTACT)
                if (id == null) {
                    if (repo.hasAcceptedContacts()) postRegular(context)
                } else if (repo.currentPick()?.id == id) {
                    postSnooze(context, id)
                }
            }
            // Phone restarted, app updated, or the clock/time zone changed: plan again.
            else -> rescheduleAll(context)
        }
    }
}

/** Receives alarm and restart broadcasts. Declared in AndroidManifest.xml. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION_DISMISSED) {
            // Swiped away: put it back after a moment, once the phone has finished removing it.
            val pending = goAsync()
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    Reminders.handle(context.applicationContext, intent)
                } finally {
                    pending.finish()
                }
            }, 700L)
            return
        }
        Reminders.handle(context, intent)
    }
}
