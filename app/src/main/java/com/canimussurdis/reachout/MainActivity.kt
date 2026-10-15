package com.canimussurdis.reachout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.canimussurdis.reachout.data.FirstRun
import com.canimussurdis.reachout.data.NotYetMode
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.data.ThemeMode
import com.canimussurdis.reachout.reminders.Reminders
import com.canimussurdis.reachout.ui.inspector.DataInspectorScreen
import com.canimussurdis.reachout.ui.settings.SettingsScreen
import com.canimussurdis.reachout.ui.swipe.SwipeScreen
import com.canimussurdis.reachout.ui.theme.ReachOutTheme
import com.canimussurdis.reachout.ui.welcome.WelcomeTour
import com.canimussurdis.reachout.ui.wheel.WheelScreen

class MainActivity : ComponentActivity() {

    /** Goes up every time the app comes back to the foreground (e.g. after a call or text). */
    private val resumeCount = mutableIntStateOf(0)

    /** Set when a reminder notification is tapped. */
    private val openRequest = mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = ReachOutRepository.get(applicationContext)
        handleIntent(intent)
        Reminders.rescheduleAll(applicationContext)
        setContent {
            AppRoot(repo, resumeCount.intValue, openRequest.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
    }

    private fun handleIntent(intent: Intent?) {
        val target = intent?.getStringExtra(Reminders.EXTRA_OPEN) ?: return
        intent.removeExtra(Reminders.EXTRA_OPEN)
        openRequest.value = OpenRequest(
            contactId = target.takeIf { it != Reminders.OPEN_WHEEL },
            nonce = System.nanoTime(),
        )
    }
}

/** "Open the wheel", optionally showing one person's result box straight away. */
data class OpenRequest(val contactId: String?, val nonce: Long)

private enum class Screen { SWIPE, WHEEL, SETTINGS, TOOLS }

@Composable
private fun AppRoot(repo: ReachOutRepository, resumeCount: Int, openRequest: OpenRequest?) {
    var settingsVersion by remember { mutableIntStateOf(0) }
    val settings = remember(settingsVersion) { repo.settings }
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Keep the status bar icons readable when the app's theme differs from the phone's.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    ReachOutTheme(darkTheme = dark) {
        // Text size setting scales all text in the app.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * settings.textScale)
        ) {
            AppScreens(repo, resumeCount, openRequest, settingsVersion) { settingsVersion++ }
        }
    }
}

@Composable
private fun AppScreens(
    repo: ReachOutRepository,
    resumeCount: Int,
    openRequest: OpenRequest?,
    settingsVersion: Int,
    onSettingsChanged: () -> Unit,
) {
    // The app always opens on the wheel. Contacts are only read when you choose to add people.
    var screen by rememberSaveable { mutableStateOf(Screen.WHEEL) }
    var settingsReturnTo by rememberSaveable { mutableStateOf(Screen.WHEEL) }
    var answered by remember { mutableIntStateOf(0) }
    val dataVersion = resumeCount + answered + settingsVersion

    // A tapped reminder always lands on the wheel.
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            screen = Screen.WHEEL
        }
    }

    var settingsAtImport by remember { mutableStateOf(false) }
    // "Add people" asks how, every time.
    var choosingSource by remember { mutableStateOf(false) }

    // Welcome tour: shown the first time the app opens after each install, and from Settings.
    val context = LocalContext.current
    var showTour by rememberSaveable { mutableStateOf(FirstRun.shouldShowTour(context)) }
    var tourIsReplay by rememberSaveable { mutableStateOf(false) }

    fun openSettings(atImport: Boolean = false) {
        settingsReturnTo = screen
        settingsAtImport = atImport
        screen = Screen.SETTINGS
    }

    /** "Add people": let the person choose how. Nothing is read from contacts until they do. */
    fun addMorePeople() {
        choosingSource = true
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val padded = Modifier.padding(innerPadding)
        if (showTour) {
            val settings = repo.settings
            WelcomeTour(
                firstTime = !tourIsReplay,
                demoOn = settings.demoMode,
                reduceMotion = settings.reduceMotion,
                onFinish = { tryDemo ->
                    FirstRun.markTourSeen(context)
                    if (tryDemo) {
                        repo.setDemoMode(true)
                        onSettingsChanged()
                    }
                    showTour = false
                    tourIsReplay = false
                },
                modifier = padded,
            )
        } else when (screen) {
            Screen.SWIPE -> {
                BackHandler { screen = Screen.WHEEL }
                SwipeScreen(
                    repo = repo,
                    onContinue = { screen = Screen.WHEEL },
                    onOpenMenu = { openSettings() },
                    modifier = padded,
                )
            }
            Screen.WHEEL -> {
                WheelScreen(
                    repo = repo,
                    dataVersion = dataVersion,
                    openRequest = openRequest,
                    onAddPeople = { addMorePeople() },
                    onOpenMenu = { openSettings() },
                    modifier = padded,
                )
            }
            Screen.SETTINGS -> SettingsScreen(
                repo = repo,
                onBack = { screen = settingsReturnTo },
                onOpenTestTools = { screen = Screen.TOOLS },
                onSettingsChanged = onSettingsChanged,
                onShowTour = {
                    tourIsReplay = true
                    showTour = true
                },
                modifier = padded,
                startAtImport = settingsAtImport,
            )
            Screen.TOOLS -> {
                BackHandler { screen = Screen.SETTINGS }
                DataInspectorScreen(repo = repo, onBack = { screen = Screen.SETTINGS }, modifier = padded)
            }
        }
    }

    if (choosingSource && !showTour) {
        val demo = repo.settings.demoMode
        AlertDialog(
            onDismissRequest = { choosingSource = false },
            title = { Text("Add people", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (demo) {
                            "Demo mode is on, so these use pretend people."
                        } else {
                            "How would you like to add people? Your contacts are only read when you choose."
                        }
                    )
                    Button(
                        onClick = {
                            choosingSource = false
                            screen = Screen.SWIPE
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Swipe through contacts") }
                    OutlinedButton(
                        onClick = {
                            choosingSource = false
                            openSettings(atImport = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick from a list or a file") }
                }
            },
            confirmButton = { TextButton(onClick = { choosingSource = false }) { Text("Not now") } },
        )
    }

    // "Did you connect?" comes first whenever you return to the app.
    if (!showTour && (screen == Screen.SWIPE || screen == Screen.WHEEL)) {
        var checks by remember(screen, resumeCount) { mutableStateOf(repo.snapshot().pendingChecks) }
        // Set while asking "Are you sure?" after "Not yet".
        var confirmingNotYet by remember { mutableStateOf(false) }
        checks.firstOrNull()?.let { contact ->
            val answer: (Boolean) -> Unit = { connected ->
                repo.answerCheck(contact.id, connected)
                checks = repo.snapshot().pendingChecks
                confirmingNotYet = false
                answered++
            }
            if (!confirmingNotYet) {
                AlertDialog(
                    onDismissRequest = {},
                    title = {
                        Text(
                            "Did you connect with ${contact.name}?",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    confirmButton = { TextButton(onClick = { answer(true) }) { Text("Yes") } },
                    dismissButton = { TextButton(onClick = { confirmingNotYet = true }) { Text("Not yet") } },
                )
            } else {
                val keepOnWheel = repo.settings.notYetMode == NotYetMode.KEEP_ON_WHEEL
                AlertDialog(
                    onDismissRequest = { confirmingNotYet = false },
                    title = { Text("Are you sure?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            if (keepOnWheel) {
                                "This will put ${contact.name} back into the pool, staying on this week's wheel."
                            } else {
                                "This will put ${contact.name} back into the pool."
                            }
                        )
                    },
                    confirmButton = { TextButton(onClick = { answer(false) }) { Text("Yes, put back") } },
                    dismissButton = { TextButton(onClick = { confirmingNotYet = false }) { Text("Go back") } },
                )
            }
        }
    }
}
