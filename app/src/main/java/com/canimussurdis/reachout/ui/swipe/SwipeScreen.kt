package com.canimussurdis.reachout.ui.swipe

import com.canimussurdis.reachout.ui.common.raised
import com.canimussurdis.reachout.ui.common.InkButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.offset
import com.canimussurdis.reachout.ui.common.AppIcons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.style.TextOverflow
import com.canimussurdis.reachout.data.Contact
import com.canimussurdis.reachout.data.KeepFrequency
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.ui.common.FrequencyPicker
import java.util.Locale
import com.canimussurdis.reachout.ui.common.AccessoryLook
import com.canimussurdis.reachout.ui.common.ContactCard
import com.canimussurdis.reachout.ui.common.MenuButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val SWIPES_BEFORE_CONTINUE = 3

/**
 * Screen 1: one contact at a time, like a dating app.
 * Swipe right to keep in touch, left to pass. "Continue" appears after 3 swipes.
 */
@Composable
fun SwipeScreen(
    repo: ReachOutRepository,
    onContinue: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    /** On app launch: if there's nobody new to swipe, go straight to the wheel. */
    autoContinueWhenEmpty: Boolean = false,
    /** Only show these people (the ones you removed); go to the wheel once they're all decided. */
    onlyIds: Set<String>? = null,
    /** People chosen in Import contacts (from your phone or a file): swipe on just these, then go to the wheel. */
    presetDeck: List<Contact>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var demoMode by remember { mutableStateOf(repo.settings.demoMode) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        askedOnce = true
    }
    // A list picked in Import contacts is already in hand, so it doesn't need contacts access.
    val canLoad = demoMode || hasPermission || presetDeck != null

    // null = still loading
    var deck by remember { mutableStateOf<List<Contact>?>(null) }
    LaunchedEffect(canLoad, demoMode, onlyIds, presetDeck) {
        deck = if (presetDeck != null) {
            // Older saved data may also list people by id only: look those up in your contacts.
            val byId = if (onlyIds.isNullOrEmpty()) {
                emptyList()
            } else {
                val all = withContext(Dispatchers.IO) { runCatching { repo.loadSwipeDeck() }.getOrDefault(emptyList()) }
                val wanted = all.filter { it.id in onlyIds }
                repo.dropReswipe(onlyIds - wanted.map { it.id }.toSet())
                wanted
            }
            repo.undecided((presetDeck + byId).distinctBy { it.id }).shuffled()
        } else if (canLoad) {
            val all = withContext(Dispatchers.IO) { runCatching { repo.loadSwipeDeck() }.getOrDefault(emptyList()) }
            if (onlyIds == null) {
                all
            } else {
                val wanted = all.filter { it.id in onlyIds }
                // Anyone missing from your contacts now can't be shown: stop waiting for them.
                repo.dropReswipe(onlyIds - wanted.map { it.id }.toSet())
                wanted
            }
        } else {
            emptyList()
        }
    }

    // A fresh random accessory for each card shown, never the same as the card before it.
    // (Plain holders, not Compose state, because they're filled in while drawing.)
    val looks = remember { mutableMapOf<String, AccessoryLook>() }
    val lastAccessory = remember { arrayOfNulls<Int>(1) }
    fun lookFor(contact: Contact): AccessoryLook = looks.getOrPut(contact.id) {
        AccessoryLook.random(avoid = lastAccessory[0]).also { lastAccessory[0] = it.accessory }
    }

    LaunchedEffect(deck) {
        if ((onlyIds != null || presetDeck != null) && canLoad && deck?.isEmpty() == true) {
            onContinue() // everyone you picked (or removed) has been decided on
        } else if (autoContinueWhenEmpty && canLoad && deck?.isEmpty() == true && repo.snapshot().accepted.isNotEmpty()) {
            onContinue()
        }
    }

    var swipes by rememberSaveable { mutableIntStateOf(0) }
    var cardWidth by remember { mutableFloatStateOf(1000f) }
    var busy by remember { mutableStateOf(false) }
    // How often the next person you keep comes up. Back to Surprise me after every swipe.
    var frequency by rememberSaveable { mutableStateOf(KeepFrequency.SURPRISE) }

    fun commit(contact: Contact, keep: Boolean) {
        if (keep) repo.accept(contact, frequency) else repo.reject(contact)
        frequency = KeepFrequency.SURPRISE
        deck = deck?.drop(1)
        swipes++
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /** Fly the card off-screen in the given direction, then record the swipe. */
    suspend fun flyOff(contact: Contact, offset: Animatable<Float, AnimationVector1D>, keep: Boolean) {
        if (busy) return
        busy = true
        offset.animateTo(if (keep) cardWidth * 1.5f else -cardWidth * 1.5f, tween(durationMillis = 200))
        commit(contact, keep)
        busy = false
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Add people",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val left = deck?.size ?: 0
                Text(
                    when {
                        !canLoad || left == 0 -> "Keep in touch, or pass"
                        onlyIds != null && presetDeck == null ->
                            "You removed ${if (left == 1) "this person" else "these people"}. Keep or pass?"
                        else -> "Keep or pass · $left to go"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MenuButton(onClick = onOpenMenu)
        }

        // Card area
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val current = deck
            when {
                !canLoad -> PermissionCard(
                    showSettingsHint = askedOnce,
                    onAllow = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onTryDemo = {
                        repo.setDemoMode(true)
                        demoMode = true
                    },
                )

                current == null -> CircularProgressIndicator()

                current.isEmpty() -> MessageCard(
                    title = "You've seen everyone",
                    body = "Tap Continue to spin the wheel. To add people another way, use Add people → Pick from a list or a file.",
                )

                else -> {
                    val top = current.first()
                    val next = current.getOrNull(1)
                    val offset = remember(top.id) { Animatable(0f) }
                    val progress = (offset.value / (cardWidth * 0.3f)).coerceIn(-1f, 1f)

                    // A third card peeks out at the back, for depth.
                    if (current.size > 2) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp)
                                .offset(y = 22.dp)
                                .graphicsLayer { alpha = 0.85f }
                                .raised(RoundedCornerShape(26.dp), 6.dp),
                        )
                    }

                    // The next card waits underneath and grows as you drag.
                    if (next != null) {
                        key(next.id) {
                            ContactCard(
                                next,
                                lookFor(next),
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val p = abs(progress)
                                        scaleX = 0.94f + 0.06f * p
                                        scaleY = 0.94f + 0.06f * p
                                        alpha = 0.6f + 0.4f * p
                                    },
                            )
                        }
                    }

                    key(top.id) {
                        ContactCard(
                            contact = top,
                            look = lookFor(top),
                            swipeProgress = progress,
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { cardWidth = it.width.toFloat().coerceAtLeast(1f) }
                                .graphicsLayer {
                                    // Swings toward you in 3D as you drag it.
                                    val p = (offset.value / (cardWidth * 0.3f)).coerceIn(-1f, 1f)
                                    translationX = offset.value
                                    rotationZ = offset.value / cardWidth * 12f
                                    rotationY = p * 14f
                                    cameraDistance = 14f * density
                                    scaleX = 1f + abs(p) * 0.03f
                                    scaleY = 1f + abs(p) * 0.03f
                                }
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction("Keep in touch") {
                                            scope.launch { flyOff(top, offset, keep = true) }
                                            true
                                        },
                                        CustomAccessibilityAction("Pass") {
                                            scope.launch { flyOff(top, offset, keep = false) }
                                            true
                                        },
                                    )
                                }
                                .pointerInput(top.id) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            scope.launch {
                                                val threshold = cardWidth * 0.3f
                                                when {
                                                    offset.value > threshold -> flyOff(top, offset, keep = true)
                                                    offset.value < -threshold -> flyOff(top, offset, keep = false)
                                                    else -> offset.animateTo(0f, spring())
                                                }
                                            }
                                        },
                                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                                    ) { change, dragAmount ->
                                        if (busy) return@detectHorizontalDragGestures
                                        change.consume()
                                        scope.launch { offset.snapTo(offset.value + dragAmount) }
                                    }
                                },
                        )
                    }
                }
            }
        }

        // How often: sits below the card and applies to whoever you keep next.
        val topCard = if (canLoad) deck?.firstOrNull() else null
        if (topCard != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val firstName = topCard.name.trim().substringBefore(' ').ifBlank { "them" }
                    Text(
                        "How often for $firstName".uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        frequency.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                FrequencyPicker(selected = frequency, onSelect = { frequency = it })
            }
        }

        // Continue appears after 3 swipes (or right away when the deck is empty).
        val deckEmpty = canLoad && deck?.isEmpty() == true
        val showContinue = swipes >= SWIPES_BEFORE_CONTINUE || deckEmpty
        AnimatedVisibility(
            visible = showContinue,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            InkButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!showContinue && canLoad && deck?.isNotEmpty() == true) {
            // Swipe hints: left to pass, right to keep in touch
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Swipe right to keep in touch, left to pass"
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HintChip(AppIcons.ArrowLeft, "Pass", iconFirst = true)
                HintChip(AppIcons.ArrowRight, "Keep", iconFirst = false)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HintChip(icon: ImageVector, label: String, iconFirst: Boolean) {
    Row(
        Modifier
            .height(38.dp)
            .raised(CircleShape, 4.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        if (iconFirst) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        if (!iconFirst) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun PermissionCard(showSettingsHint: Boolean, onAllow: () -> Unit, onTryDemo: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().raised(RoundedCornerShape(26.dp), 14.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Let's find your people",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "Reach Out reads your contacts to show them as cards. It can never change or delete them.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InkButton(onClick = onAllow, modifier = Modifier.fillMaxWidth(), height = 52.dp) { Text("Allow contacts access") }
            TextButton(onClick = onTryDemo) { Text("Try with demo contacts instead") }
            if (showSettingsHint) {
                Text(
                    "If nothing happens, allow Contacts in your phone's Settings → Apps → Reach Out → Permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, body: String) {
    Card(
        Modifier.fillMaxSize().raised(RoundedCornerShape(26.dp), 14.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
