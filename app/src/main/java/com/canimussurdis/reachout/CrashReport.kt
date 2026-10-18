package com.canimussurdis.reachout

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * If the app crashes, the error is saved to the app's private folder. The next time the app opens,
 * it shows the error with Copy and Share buttons instead of crashing again, so it can be reported.
 * Nothing is sent anywhere automatically.
 */
object CrashReport {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val version = runCatching {
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName
                }.getOrNull()
                val text = buildString {
                    appendLine("ReachOut crashed")
                    appendLine("When: ${java.time.ZonedDateTime.now()}")
                    appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                    appendLine("App version: ${version ?: "?"}")
                    appendLine()
                    append(error.stackTraceToString().take(20_000))
                }
                File(app.filesDir, FILE).writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}

/** Shown instead of the app after a crash, so the error can be copied or shared. */
@Composable
fun CrashScreen(report: String, onTryAgain: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ReachOut hit a problem", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "The app closed unexpectedly last time. Tap Copy or Share and send this to whoever is fixing the app. " +
                    "Your list and settings are still saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("ReachOut crash", report))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text("Copy") }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }, modifier = Modifier.weight(1f)) { Text("Share") }
            }
            TextButton(onClick = onTryAgain) { Text("Open the app again") }
            Text(
                report,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}
