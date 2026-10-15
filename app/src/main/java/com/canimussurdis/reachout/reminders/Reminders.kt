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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Reminders use Android's built-in AlarmManager (no extra libraries, no exact-alarm permission).
 * Alarms are "inexact", so Android may deliver them a few minutes late to save battery.
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
        val now = System.currentTimeMillis()

        val regular = regularIntent(context)
        val next = nextRegularReminder(now, repo.settings)
        if (next == null) {
            alarms.cancel(regular)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, regular)
        }
        repo.changeSettings { it.copy(nextReminderAt = next) }

        repo.settings.snoozes.forEach { (id, at) ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(at, now + 60_000L), snoozeIntent(context, id))
        }
    }

    /** "Remind me in 1 hour" — or when the reminder window next opens, if that hour falls outside it. */
    fun snoozeOneHour(context: Context, contactId: String): Long {
        val repo = ReachOutRepository.get(context)
        val at = fitIntoWindow(System.currentTimeMillis() + 60 * 60_000L, repo.settings)
        scheduleSnoozeAt(context, contactId, at)
        return at
    }

    /** Test tools only: a snooze reminder in about a minute, ignoring the window. */
    fun snoozeForTesting(context: Context, contactId: String) =
        scheduleSnoozeAt(context, contactId, System.currentTimeMillis() + 60_000L)

    private fun scheduleSnoozeAt(context: Context, contactId: String, at: Long) {
        ReachOutRepository.get(context).changeSettings { it.copy(snoozes = it.snoozes + (contactId to at)) }
        context.getSystemService(AlarmManager::class.java)
            ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, snoozeIntent(context, contactId))
    }

    /**
     * When the next regular reminder should go off, or null if reminders are off.
     * Lands on a random minute inside the reminder window on the right day.
     */
    fun nextRegularReminder(nowMs: Long, s: Settings, random: Random = Random.Default): Long? {
        if (!s.remindersOn) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()
        val (startMin, endMin) = window(s)
        val snapsToDay = s.reminderFrequency != ReminderFrequency.DAILY

        val last = s.lastReminderAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        var date: LocalDate = last?.let {
            when (s.reminderFrequency) {
                ReminderFrequency.DAILY -> it.plusDays(1)
                ReminderFrequency.WEEKLY -> it.plusWeeks(1)
                ReminderFrequency.BIWEEKLY -> it.plusWeeks(2)
                ReminderFrequency.MONTHLY -> it.plusMonths(1)
                ReminderFrequency.YEARLY -> it.plusYears(1)
            }
        } ?: today
        if (date.isBefore(today)) date = today
        if (snapsToDay) date = date.with(TemporalAdjusters.nextOrSame(s.reminderDay))

        // Today's window already over? Move to the next allowed day.
        val nowMin = now.hour * 60 + now.minute
        if (date == today && nowMin >= endMin - 1) {
            date = if (snapsToDay) date.with(TemporalAdjusters.next(s.reminderDay)) else date.plusDays(1)
        }

        val fromMin = if (date == today) maxOf(startMin, nowMin + 2) else startMin
        val minute = fromMin + random.nextInt(maxOf(1, endMin - fromMin))
        return at(date, minute, zone)
    }

    /** Moves a time to the reminder window: unchanged if inside, else the window's next opening. */
    fun fitIntoWindow(timeMs: Long, s: Settings): Long {
        val zone = ZoneId.systemDefault()
        val t: ZonedDateTime = Instant.ofEpochMilli(timeMs).atZone(zone)
        val (startMin, endMin) = window(s)
        val minute = t.hour * 60 + t.minute
        return when {
            minute < startMin -> at(t.toLocalDate(), startMin, zone)
            minute >= endMin -> at(t.toLocalDate().plusDays(1), startMin, zone)
            else -> timeMs
        }
    }

    private fun window(s: Settings): Pair<Int, Int> {
        val start = s.windowStartMin.coerceIn(0, 23 * 60)
        val end = s.windowEndMin.coerceIn(start + 30, 24 * 60 - 1)
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
                repo.changeSettings { it.copy(lastReminderAt = System.currentTimeMillis()) }
                if (repo.settings.remindersOn && repo.hasAcceptedContacts()) postRegular(context)
                rescheduleAll(context)
            }
            ACTION_SNOOZE -> {
                val id = intent.getStringExtra(EXTRA_CONTACT) ?: return
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
