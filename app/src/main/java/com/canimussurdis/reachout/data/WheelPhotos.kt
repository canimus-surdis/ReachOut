package com.canimussurdis.reachout.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Your own photos for the hidden wheel picture. Picked with Android's photo picker
 * (no permission needed), then copied, shrunk, into the app's private folder.
 * Your gallery is never changed.
 */
object WheelPhotos {

    private const val MAX_SIDE = 1600

    /** The only names the app ever gives photos. Anything else (like "../settings.json") is refused. */
    private val SAFE_NAME = Regex("""photo_[A-Za-z0-9-]+\.jpg""")

    fun isSafeName(name: String): Boolean = SAFE_NAME.matches(name)

    private fun folder(context: Context) = File(context.filesDir, "wheel_photos").apply { mkdirs() }

    /**
     * A photo's file, always inside the photos folder. A name the app didn't make (for example from a
     * damaged or tampered backup) points at a file that never exists, so nothing outside can be read or deleted.
     */
    fun file(context: Context, name: String) = File(folder(context), if (isSafeName(name)) name else ".invalid")

    /** Copies picked photos in. Returns the new file names (photos that fail to load are skipped). */
    fun import(context: Context, uris: List<Uri>): List<String> = uris.mapNotNull { uri ->
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val name = "photo_${UUID.randomUUID()}.jpg"
            FileOutputStream(file(context, name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }.getOrNull()
    }

    fun delete(context: Context, name: String) {
        file(context, name).delete()
    }

    /** Loads a photo, shrunk if it's very large, so an oversized file can't run the phone out of memory. */
    fun load(context: Context, name: String): Bitmap? = runCatching {
        val path = file(context, name).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2 * MAX_SIDE) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /**
     * Which picture hides behind this week's wheel: a photo file name, or null for a generated design.
     * Fixed for the whole week (based on when the batch was drawn).
     */
    fun choose(seed: Long, s: Settings): String? {
        val photos = s.wheelPhotos
        if (photos.isEmpty()) return null
        val pick = photos[Math.floorMod(seed.hashCode(), photos.size)]
        return when (s.pictureSource) {
            PictureSource.DESIGNS -> null
            PictureSource.PHOTOS -> pick
            PictureSource.MIX -> if (Math.floorMod((seed / 7).hashCode(), 2) == 0) null else pick
        }
    }
}
