package com.canimussurdis.reachout.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore as one .zip file, saved wherever you choose with Android's file picker
 * (Downloads, Google Drive, a USB stick...). No permissions needed.
 *
 * Contains: settings, your list (accepted, passed, cooldowns, this week's wheel) and, optionally,
 * your wheel photos. Demo data is never included.
 */
object Backup {

    private const val INFO = "reachout-backup.json"
    private const val PHOTO_DIR = "wheel_photos/"
    private val SAFE_PHOTO_NAME = Regex("""photo_[A-Za-z0-9-]+\.jpg""")
    private const val MAX_ENTRY_BYTES = 25L * 1024 * 1024

    data class Summary(val people: Int, val photos: Int)

    /** Default file name, e.g. ReachOut-backup-2026-10-15.zip */
    fun suggestedFileName(): String = "ReachOut-backup-${java.time.LocalDate.now()}.zip"

    fun export(context: Context, out: OutputStream, includePhotos: Boolean): Summary {
        val repo = ReachOutRepository.get(context)
        val settingsJson = readJson(repo.dataFile(ReachOutRepository.SETTINGS_FILE)) ?: JSONObject()
        val stateJson = readJson(repo.dataFile(ReachOutRepository.STATE_FILE)) ?: JSONObject()

        // Never carry demo mode or this phone's pending alarms into a backup.
        settingsJson.put("demoMode", false)
        settingsJson.put("demoTimeOffsetMs", 0)
        settingsJson.remove("snoozes")
        settingsJson.remove("nextReminderAt")
        val photos = if (includePhotos) repo.settings.wheelPhotos.filter { WheelPhotos.file(context, it).exists() } else emptyList()
        settingsJson.put("wheelPhotos", JSONArray(photos))

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(INFO))
            zip.write(
                JSONObject()
                    .put("app", "Reach Out")
                    .put("format", 1)
                    .put("createdAt", System.currentTimeMillis())
                    .toString(2).toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ReachOutRepository.SETTINGS_FILE))
            zip.write(settingsJson.toString(2).toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(ReachOutRepository.STATE_FILE))
            zip.write(stateJson.toString(2).toByteArray())
            zip.closeEntry()

            photos.forEach { name ->
                zip.putNextEntry(ZipEntry(PHOTO_DIR + name))
                WheelPhotos.file(context, name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return Summary(people = countPeople(stateJson), photos = photos.size)
    }

    /**
     * Reads a backup and checks it fully before changing anything.
     * Throws IllegalArgumentException with a friendly message if the file isn't a Reach Out backup.
     */
    fun read(input: InputStream): Contents {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val allowed = name == INFO || name == ReachOutRepository.SETTINGS_FILE ||
                    name == ReachOutRepository.STATE_FILE ||
                    (name.startsWith(PHOTO_DIR) && SAFE_PHOTO_NAME.matches(name.removePrefix(PHOTO_DIR)))
                if (allowed && !entry.isDirectory) entries[name] = readLimited(zip)
                zip.closeEntry()
            }
        }
        if (INFO !in entries) throw IllegalArgumentException("That file isn't a Reach Out backup.")
        val settings = parse(entries[ReachOutRepository.SETTINGS_FILE])
            ?: throw IllegalArgumentException("The backup's settings are missing or damaged.")
        val state = parse(entries[ReachOutRepository.STATE_FILE])
            ?: throw IllegalArgumentException("The backup's contact list is missing or damaged.")
        val photos = entries.filterKeys { it.startsWith(PHOTO_DIR) }.mapKeys { it.key.removePrefix(PHOTO_DIR) }
        return Contents(settings, state, photos)
    }

    class Contents internal constructor(
        internal val settings: JSONObject,
        internal val state: JSONObject,
        internal val photos: Map<String, ByteArray>,
    ) {
        val people: Int get() = countPeople(state)
    }

    /** Replaces the app's data with the backup's. */
    fun restore(context: Context, contents: Contents): Summary {
        val repo = ReachOutRepository.get(context)

        // Photos first, then keep only the photo names that actually arrived.
        contents.photos.forEach { (name, bytes) -> WheelPhotos.file(context, name).writeBytes(bytes) }
        val listed = contents.settings.optJSONArray("wheelPhotos") ?: JSONArray()
        val kept = (0 until listed.length()).map { listed.getString(it) }
            .filter { WheelPhotos.file(context, it).exists() }
        contents.settings.put("wheelPhotos", JSONArray(kept))
        contents.settings.put("demoMode", false)
        contents.settings.put("demoTimeOffsetMs", 0)

        writeAtomically(repo.dataFile(ReachOutRepository.STATE_FILE), contents.state.toString())
        writeAtomically(repo.dataFile(ReachOutRepository.SETTINGS_FILE), contents.settings.toString())
        repo.dataFile(ReachOutRepository.DEMO_STATE_FILE).delete()
        repo.reloadFromDisk()
        return Summary(people = contents.people, photos = kept.size)
    }

    // ---------- helpers ----------

    private fun countPeople(state: JSONObject): Int {
        val records = state.optJSONArray("records") ?: return 0
        return (0 until records.length()).count { records.optJSONObject(it)?.optString("status") == "ACCEPTED" }
    }

    private fun readJson(file: File): JSONObject? =
        if (!file.exists()) null else runCatching { JSONObject(file.readText()) }.getOrNull()

    private fun parse(bytes: ByteArray?): JSONObject? =
        bytes?.let { runCatching { JSONObject(String(it)) }.getOrNull() }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw IllegalArgumentException("That backup file is too large.")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun writeAtomically(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
