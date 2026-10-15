package com.canimussurdis.reachout.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves and loads the app's data as small JSON files in the app's private folder.
 * Uses Android's built-in org.json, so no extra libraries are needed.
 */
internal object Storage {

    // ---------- Settings ----------

    fun readSettings(file: File): Settings {
        val o = readJson(file) ?: return Settings()
        val d = Settings()
        return Settings(
            demoMode = o.optBoolean("demoMode", d.demoMode),
            demoTimeOffsetMs = o.optLong("demoTimeOffsetMs", d.demoTimeOffsetMs),
            batchDay = enumOr(o.optString("batchDay"), d.batchDay),
            wheelSize = o.optInt("wheelSize", d.wheelSize),
            cooldownMinDays = o.optInt("cooldownMinDays", d.cooldownMinDays),
            cooldownMaxDays = o.optInt("cooldownMaxDays", d.cooldownMaxDays),
            reachedMode = enumOr(o.optString("reachedMode"), d.reachedMode),
            notYetMode = enumOr(o.optString("notYetMode"), d.notYetMode),
            message = readMessage(o, d),
            spinSound = o.optBoolean("spinSound", d.spinSound),
            spinVibration = o.optBoolean("spinVibration", d.spinVibration),
            reduceMotion = o.optBoolean("reduceMotion", d.reduceMotion),
            remindersOn = o.optBoolean("remindersOn", d.remindersOn),
            reminderFrequency = readFrequency(o, d),
            reminderDay = enumOr(o.optString("reminderDay"), d.reminderDay),
            // Older versions had one weekly day: start the multi-day choice from it.
            reminderDays = strings(o.optJSONArray("reminderDays"))
                .mapNotNull { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() }.toSet()
                .ifEmpty { setOf(enumOr(o.optString("reminderDay"), d.reminderDay)) },
            reminderDayOfMonth = o.optInt("reminderDayOfMonth", d.reminderDayOfMonth).coerceIn(0, 31),
            reminderMonths = strings(o.optJSONArray("reminderMonths"))
                .mapNotNull { runCatching { java.time.Month.valueOf(it) }.getOrNull() }.toSet(),
            windowStartMin = o.optInt("windowStartMin", d.windowStartMin),
            windowEndMin = o.optInt("windowEndMin", d.windowEndMin),
            lastReminderAt = if (o.has("lastReminderAt")) o.getLong("lastReminderAt") else null,
            nextReminderAt = if (o.has("nextReminderAt")) o.getLong("nextReminderAt") else null,
            snoozes = o.optJSONObject("snoozes")?.let { s ->
                s.keys().asSequence().associateWith { s.getLong(it) }
            } ?: emptyMap(),
            askedNotificationPermission = o.optBoolean("askedNotificationPermission", d.askedNotificationPermission),
            themeMode = enumOr(o.optString("themeMode"), d.themeMode),
            textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat(),
            palette = enumOr(o.optString("palette"), d.palette),
            customMessages = strings(o.optJSONArray("customMessages")),
            pictureSource = enumOr(o.optString("pictureSource"), d.pictureSource),
            wheelPhotos = strings(o.optJSONArray("wheelPhotos")),
        )
    }

    /** Old app versions saved their default message automatically. Settings saved before then switch to Blank. */
    private const val MESSAGES_VERSION = 2
    private val OLD_DEFAULT_MESSAGES = setOf("Thinking about you", "Hey! It's been a while. How are you doing?")

    private fun readMessage(o: JSONObject, d: Settings): String {
        val stored = if (o.has("message")) o.optString("message") else return d.message // "" = Blank preset
        val old = o.optInt("messagesVersion", 1) < MESSAGES_VERSION
        return if (old && stored in OLD_DEFAULT_MESSAGES) Settings.BLANK_MESSAGE else stored
    }

    /** Old app versions saved Weekly automatically as the default. Settings saved before the switch to Daily move to Daily once. */
    private const val REMINDERS_VERSION = 2

    private fun readFrequency(o: JSONObject, d: Settings): ReminderFrequency {
        val stored = enumOr(o.optString("reminderFrequency"), d.reminderFrequency)
        val old = o.has("reminderFrequency") && o.optInt("remindersVersion", 1) < REMINDERS_VERSION
        return if (old && stored == ReminderFrequency.WEEKLY) ReminderFrequency.DAILY else stored
    }

    fun writeSettings(file: File, s: Settings) {
        val o = JSONObject()
            .put("demoMode", s.demoMode)
            .put("demoTimeOffsetMs", s.demoTimeOffsetMs)
            .put("batchDay", s.batchDay.name)
            .put("wheelSize", s.wheelSize)
            .put("cooldownMinDays", s.cooldownMinDays)
            .put("cooldownMaxDays", s.cooldownMaxDays)
            .put("reachedMode", s.reachedMode.name)
            .put("notYetMode", s.notYetMode.name)
            .put("message", s.message)
            .put("messagesVersion", MESSAGES_VERSION)
            .put("spinSound", s.spinSound)
            .put("spinVibration", s.spinVibration)
            .put("reduceMotion", s.reduceMotion)
            .put("remindersOn", s.remindersOn)
            .put("reminderFrequency", s.reminderFrequency.name)
            .put("remindersVersion", REMINDERS_VERSION)
            .put("reminderDay", s.reminderDay.name)
            .put("reminderDays", JSONArray(s.reminderDays.sortedBy { it.value }.map { it.name }))
            .put("reminderMonths", JSONArray(s.reminderMonths.sortedBy { it.value }.map { it.name }))
            .put("reminderDayOfMonth", s.reminderDayOfMonth)
            .put("windowStartMin", s.windowStartMin)
            .put("windowEndMin", s.windowEndMin)
            .put("snoozes", JSONObject().apply { s.snoozes.forEach { (id, at) -> put(id, at) } })
            .put("askedNotificationPermission", s.askedNotificationPermission)
            .put("themeMode", s.themeMode.name)
            .put("textScale", s.textScale.toDouble())
            .put("palette", s.palette.name)
            .put("customMessages", JSONArray(s.customMessages))
            .put("pictureSource", s.pictureSource.name)
            .put("wheelPhotos", JSONArray(s.wheelPhotos))
        s.lastReminderAt?.let { o.put("lastReminderAt", it) }
        s.nextReminderAt?.let { o.put("nextReminderAt", it) }
        writeJson(file, o)
    }

    // ---------- App state ----------

    fun readState(file: File): AppState {
        val o = readJson(file) ?: return AppState()
        val state = AppState()
        val records = o.optJSONArray("records") ?: JSONArray()
        for (i in 0 until records.length()) {
            val r = recordFromJson(records.getJSONObject(i)) ?: continue
            state.records[r.contact.id] = r
        }
        state.batch = o.optJSONObject("batch")?.let(::batchFromJson)
        state.currentPick = if (o.has("currentPick")) o.getString("currentPick") else null
        state.pickSnoozed = state.currentPick != null && o.optBoolean("pickSnoozed", false)
        state.reswipe += strings(o.optJSONArray("reswipe"))
        return state
    }

    fun writeState(file: File, state: AppState) {
        val o = JSONObject()
            .put("records", JSONArray(state.records.values.map(::recordToJson)))
        state.batch?.let { o.put("batch", batchToJson(it)) }
        state.currentPick?.let { o.put("currentPick", it) }
        if (state.pickSnoozed) o.put("pickSnoozed", true)
        if (state.reswipe.isNotEmpty()) o.put("reswipe", JSONArray(state.reswipe.toList()))
        writeJson(file, o)
    }

    // ---------- Pieces ----------

    private fun phoneToJson(p: PhoneNumber) = JSONObject()
        .put("number", p.number)
        .put("label", p.label)
        .put("isDefault", p.isDefault)
        .put("isMobile", p.isMobile)

    private fun phoneFromJson(o: JSONObject) = PhoneNumber(
        number = o.getString("number"),
        label = o.optString("label"),
        isDefault = o.optBoolean("isDefault"),
        isMobile = o.optBoolean("isMobile"),
    )

    private fun contactToJson(c: Contact): JSONObject {
        val o = JSONObject()
            .put("id", c.id)
            .put("name", c.name)
            .put("phones", JSONArray(c.phones.map(::phoneToJson)))
        c.photoUri?.let { o.put("photoUri", it) }
        return o
    }

    private fun contactFromJson(o: JSONObject): Contact {
        val phones = o.optJSONArray("phones") ?: JSONArray()
        return Contact(
            id = o.getString("id"),
            name = o.getString("name"),
            photoUri = if (o.isNull("photoUri")) null else o.getString("photoUri"),
            phones = (0 until phones.length()).map { phoneFromJson(phones.getJSONObject(it)) },
        )
    }

    private fun recordToJson(r: ContactRecord): JSONObject {
        val o = JSONObject()
            .put("contact", contactToJson(r.contact))
            .put("status", r.status.name)
            .put("decidedAt", r.decidedAt)
        r.cooldownUntil?.let { o.put("cooldownUntil", it) }
        r.pendingCheckAt?.let { o.put("pendingCheckAt", it) }
        r.customMinDays?.let { o.put("customMinDays", it) }
        r.customMaxDays?.let { o.put("customMaxDays", it) }
        return o
    }

    private fun recordFromJson(o: JSONObject): ContactRecord? = runCatching {
        ContactRecord(
            contact = contactFromJson(o.getJSONObject("contact")),
            status = Status.valueOf(o.getString("status")),
            decidedAt = o.optLong("decidedAt"),
            cooldownUntil = if (o.has("cooldownUntil")) o.getLong("cooldownUntil") else null,
            pendingCheckAt = if (o.has("pendingCheckAt")) o.getLong("pendingCheckAt") else null,
            customMinDays = if (o.has("customMinDays")) o.getInt("customMinDays") else null,
            customMaxDays = if (o.has("customMaxDays")) o.getInt("customMaxDays") else null,
        )
    }.getOrNull()

    private fun batchToJson(b: WheelBatch): JSONObject {
        val slots = JSONArray(b.slots.map { JSONObject().put("id", it.contactId).put("state", it.state.name) })
        return JSONObject()
            .put("drawnAt", b.drawnAt)
            .put("slots", slots)
            .put("retired", JSONArray(b.retired.toList()))
            .put("skipped", JSONArray(b.skipped.toList()))
    }

    private fun batchFromJson(o: JSONObject): WheelBatch? = runCatching {
        val slots = o.getJSONArray("slots")
        val retired = o.optJSONArray("retired") ?: JSONArray()
        WheelBatch(
            drawnAt = o.getLong("drawnAt"),
            slots = (0 until slots.length()).map {
                val s = slots.getJSONObject(it)
                Slot(s.getString("id"), enumOr(s.optString("state"), SlotState.ACTIVE))
            },
            retired = (0 until retired.length()).map { retired.getString(it) }.toSet(),
            skipped = strings(o.optJSONArray("skipped")).toSet(),
        )
    }.getOrNull()

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    // ---------- File helpers ----------

    private fun readJson(file: File): JSONObject? =
        if (!file.exists()) null else runCatching { JSONObject(file.readText()) }.getOrNull()

    /** Write to a temp file first, then swap it in, so a crash mid-save can't corrupt the data. */
    private fun writeJson(file: File, o: JSONObject) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(o.toString())
            tmp.delete()
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)
}
