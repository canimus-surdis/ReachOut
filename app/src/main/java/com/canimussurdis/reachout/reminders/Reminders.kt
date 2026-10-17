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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.canimussurdis.reachout.MainActivity
import com.canimussurdis.reachout.R
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.data.ReminderFrequency
import com.canimussurdis.reachout.data.Settings
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
    private const val EXTRA_CONTACT = "contact"
    private const val REGULAR_ID = 1

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
        val gone = ids.toSet()
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

    private fun postRegular(context: Context) {
        // Someone was snoozed and is still waiting: name them instead of the usual nudge.
        val repo = ReachOutRepository.get(context)
        val waiting = if (repo.isPickSnoozed()) repo.currentPick() else null
        if (waiting != null) {
            post(
                context,
                id = REGULAR_ID,
                title = "Reminder: reach out to ${waiting.name}",
                text = "Tap to call, text or share.",
                open = waiting.id,
            )
            return
        }
        post(
            context,
            id = REGULAR_ID,
            title = "Time to reach out to someone",
            text = "Spin the wheel and say hi.",
            open = OPEN_WHEEL,
        )
    }

    private fun postSnooze(context: Context, contactId: String) {
        val name = ReachOutRepository.get(context).contactById(contactId)?.name
        post(
            context,
            id = snoozeRequestCode(contactId),
            title = if (name != null) "Reminder: reach out to $name" else "Time to reach out to someone",
            text = if (name != null) "Tap to call, text or share." else "Spin the wheel and say hi.",
            open = if (name != null) contactId else OPEN_WHEEL,
        )
    }

    @SuppressLint("MissingPermission") // checked by notificationsAllowed() just below
    private fun post(context: Context, id: Int, title: String, text: String, open: String) {
        if (!notificationsAllowed(context)) return
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB95C40.toInt()) // terracotta, to match the app
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
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
                postSnooze(context, id)
            }
            // Phone restarted, app updated, or the clock/time zone changed: plan again.
            else -> rescheduleAll(context)
        }
    }
}

/** Receives alarm and restart broadcasts. Declared in AndroidManifest.xml. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.handle(context, intent)
    }
}
