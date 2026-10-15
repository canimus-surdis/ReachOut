package com.canimussurdis.reachout.data

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

/**
 * Remembers whether the welcome tour has been shown since this copy of the app was installed.
 *
 * The marker lives in Android's "no backup" folder, so it is never copied to a new phone or
 * restored after a reinstall, and it records the install time, so every fresh install shows the
 * tour again. Updates (including pressing Run in Android Studio) keep the same install time.
 */
object FirstRun {

    private const val MARKER = "welcome_tour_seen"

    fun shouldShowTour(context: Context): Boolean {
        val seenFor = runCatching { marker(context).readText().trim().toLong() }.getOrNull()
        return seenFor != installTime(context)
    }

    fun markTourSeen(context: Context) {
        runCatching { marker(context).writeText(installTime(context).toString()) }
    }

    private fun marker(context: Context) = File(context.noBackupFilesDir, MARKER)

    private fun installTime(context: Context): Long = runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            .firstInstallTime
    }.getOrDefault(0L)
}
