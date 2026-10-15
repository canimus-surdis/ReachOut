package com.canimussurdis.reachout.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Where a guide section's button goes. */
internal enum class GuideTarget { CONTACTS, IMPORT, BREAKS, WHEEL, REMINDERS, MESSAGES, DISPLAY, BACKUP, DEMO, TOUR }

internal class GuideItem(val name: String, val text: String)

internal class GuideSection(
    val title: String,
    val summary: String,
    val items: List<GuideItem>,
    val target: GuideTarget? = null,
    val buttonLabel: String = "",
)

/** Settings → How-to guide. Plain-language help for every setting. */
internal val GUIDE: List<GuideSection> = listOf(
    GuideSection(
        title = "The basics",
        summary = "How Reach Out works, in one minute",
        items = listOf(
            GuideItem(
                "Swipe",
                "Swipe right to keep someone on your list, or left to pass. A Continue button appears after 3 swipes, " +
                    "but you can keep swiping as long as you like.",
            ),
            GuideItem(
                "The wheel",
                "On your batch day (Sunday unless you change it), up to 12 random people from your list go on the wheel. " +
                    "People on a break are left off. Tap Spin to let it choose.",
            ),
            GuideItem(
                "Your pick",
                "Call opens your dialer, Text opens your messages app with your message filled in, and Share lets you send " +
                    "the message through any app. Snooze reminds you again in an hour, or puts them back in the pool. " +
                    "The box stays open until you choose one.",
            ),
            GuideItem(
                "Did you connect?",
                "When you come back to the app, it asks whether you connected. Yes gives that person a break before they " +
                    "can come up again. Not yet does what you chose in Wheel → When you answer \"Not yet\".",
            ),
            GuideItem(
                "Saving",
                "Every change in Settings is saved straight away. There's no Save button to remember.",
            ),
        ),
    ),
    GuideSection(
        title = "Contacts",
        summary = "Who's on your list, who's resting, and who you passed",
        target = GuideTarget.CONTACTS,
        buttonLabel = "Open Contacts",
        items = listOf(
            GuideItem("+ Import contacts", "Add people without swiping. See Import contacts below."),
            GuideItem("Custom break lengths", "Give chosen people their own break length. See Custom break lengths below."),
            GuideItem(
                "In cooldown",
                "People resting after you connected, with the date each one comes back. " +
                    "Bring back now ends the break straight away so they can go on the wheel again.",
            ),
            GuideItem(
                "On your list",
                "Everyone you kept. Anyone with their own break shows it underneath, for example \"Break: 2 weeks – 1 month\". " +
                    "Remove takes someone off your list and the wheel, and puts them back in the swipe deck.",
            ),
            GuideItem(
                "Passed",
                "People you swiped left on. Restore puts them back in the swipe deck so you can decide again.",
            ),
        ),
    ),
    GuideSection(
        title = "Import contacts",
        summary = "Add lots of people at once",
        target = GuideTarget.IMPORT,
        buttonLabel = "Open Import contacts",
        items = listOf(
            GuideItem(
                "Pick several at once",
                "Tick people from your phone's contacts, then tap Add. Use Search, Select all and Clear to move faster.",
            ),
            GuideItem(
                "Add everyone",
                "Adds everyone in your phone you haven't swiped on yet. You'll be asked to confirm first.",
            ),
            GuideItem(
                "From a contacts file (.vcf)",
                "Choose a contacts file exported from Google Contacts, iCloud, Outlook or another phone, then untick " +
                    "anyone you don't want. This works even without contacts access.",
            ),
            GuideItem(
                "Good to know",
                "The first two options need contacts access, which you allow on the swipe screen. People already on your " +
                    "list, or ones you passed on, are skipped. Your phone's contacts are never changed.",
            ),
        ),
    ),
    GuideSection(
        title = "Custom break lengths",
        summary = "Different break lengths for different people",
        target = GuideTarget.BREAKS,
        buttonLabel = "Open Custom break lengths",
        items = listOf(
            GuideItem(
                "Choose people",
                "Tick one or more people. Each one shows Default or their custom length. Search and Select all help with " +
                    "long lists.",
            ),
            GuideItem(
                "Shortest and Longest",
                "Set the range, from 1 week up to 1 year. Each time you connect, the app picks a random break inside it.",
            ),
            GuideItem("Apply", "Saves the range for everyone you ticked."),
            GuideItem("Reset to default", "Puts the people you ticked back on the default from Wheel → Break after you connect."),
            GuideItem(
                "When it starts",
                "A new length is used the next time you connect with that person. A break that's already running isn't changed.",
            ),
        ),
    ),
    GuideSection(
        title = "Wheel",
        summary = "When names change, break lengths, and what happens after each pick",
        target = GuideTarget.WHEEL,
        buttonLabel = "Open Wheel",
        items = listOf(
            GuideItem(
                "New names each week on",
                "The day a fresh set of up to 12 names is drawn. The default is Sunday. If the wheel empties before then, " +
                    "it's refilled straight away.",
            ),
            GuideItem(
                "Break after you connect",
                "After you answer Yes, that person rests for a random time between Shortest and Longest before they can " +
                    "come up again. The default is 1 week to 6 months. Change it for some people with Contacts → " +
                    "Custom break lengths.",
            ),
            GuideItem(
                "When you reach someone",
                "New name (the default): a random new person takes their spot, so the wheel stays full. " +
                    "Reveal picture: their slice uncovers part of a hidden picture instead. Reach everyone to see the whole thing.",
            ),
            GuideItem(
                "When you answer \"Not yet\"",
                "Back to pool (the default): they leave the wheel without a break and can come up again, even this week. " +
                    "With New name, someone else takes their spot. Keep on wheel: they stay on this week's wheel.",
            ),
            GuideItem(
                "Hidden picture",
                "Only used with Reveal picture. Designs are colorful patterns the app makes. My photos uses pictures you add. " +
                    "Mix uses both. Add photos copies pictures into the app (your gallery isn't changed). Tap ✕ on a photo to remove it.",
            ),
        ),
    ),
    GuideSection(
        title = "Reminders",
        summary = "A gentle nudge to spin the wheel",
        target = GuideTarget.REMINDERS,
        buttonLabel = "Open Reminders",
        items = listOf(
            GuideItem("Reminders", "Turns the nudges on or off."),
            GuideItem(
                "Notifications are off",
                "Shows only if your phone is blocking Reach Out's notifications. Open notification settings takes you " +
                    "straight there.",
            ),
            GuideItem("How often", "Daily, Weekly, Every 2 weeks, Monthly or Yearly."),
            GuideItem(
                "On",
                "Which day of the week reminders come. Monthly and Yearly arrive on that day, about a month or a year " +
                    "after the last one. Hidden when How often is Daily.",
            ),
            GuideItem(
                "Only between",
                "The hours a reminder can arrive, set in half-hour steps and at least an hour long. The default is " +
                    "5 pm to 9 pm. Each reminder lands at a random time inside it. 1-hour snoozes also wait for this window.",
            ),
            GuideItem("Next reminder", "When the next nudge is planned."),
            GuideItem(
                "Battery tip",
                "Samsung and OnePlus phones can delay reminders to save battery. Set Settings → Apps → Reach Out → " +
                    "Battery to Unrestricted so they arrive on time.",
            ),
        ),
    ),
    GuideSection(
        title = "Messages",
        summary = "The words that fill in when you tap Text or Share",
        target = GuideTarget.MESSAGES,
        buttonLabel = "Open Messages",
        items = listOf(
            GuideItem(
                "Messages",
                "Five ready-made messages. Tap one to use it for Text and Share. The default is \"Thinking about you\".",
            ),
            GuideItem(
                "Your own",
                "+ Write your own saves as many messages as you like. Edit or Delete them any time. If you delete the one " +
                    "you're using, it switches back to the default.",
            ),
            GuideItem(
                "Good to know",
                "You can still change the words in your texting app before you send. Nothing is ever sent automatically.",
            ),
        ),
    ),
    GuideSection(
        title = "Display",
        summary = "Colors, text size, motion and sound",
        target = GuideTarget.DISPLAY,
        buttonLabel = "Open Display",
        items = listOf(
            GuideItem("Theme", "Follow phone matches your phone's light or dark mode. Light and Dark keep it one way."),
            GuideItem(
                "Text size",
                "Normal, Large or Extra large. This is on top of your phone's own font size.",
            ),
            GuideItem(
                "Reduce motion",
                "The wheel fades to its pick instead of spinning, and the welcome tour stays still.",
            ),
            GuideItem("Spin sound", "Soft ticks as names pass the pointer."),
            GuideItem("Spin vibration", "Light taps as names pass the pointer."),
        ),
    ),
    GuideSection(
        title = "Backup & restore",
        summary = "Keep a copy of your list and settings",
        target = GuideTarget.BACKUP,
        buttonLabel = "Open Backup & restore",
        items = listOf(
            GuideItem(
                "Save backup file",
                "Saves your list, passed people, cooldowns, custom breaks, this week's wheel, messages and settings into " +
                    "one file. You choose where it goes, such as Downloads or Google Drive.",
            ),
            GuideItem("Include wheel photos", "Adds your hidden-picture photos to the file. This makes the file bigger."),
            GuideItem(
                "Restore from file",
                "Replaces everything in the app with a backup. Use it on a new phone or after reinstalling. " +
                    "You'll be asked to confirm first.",
            ),
            GuideItem(
                "Keep it private",
                "A backup holds the names and phone numbers of people on your list, so store it somewhere private. " +
                    "Demo data isn't included.",
            ),
        ),
    ),
    GuideSection(
        title = "Demo mode & testing",
        summary = "Try things out with pretend people",
        target = GuideTarget.DEMO,
        buttonLabel = "Open Demo mode & testing",
        items = listOf(
            GuideItem(
                "Demo mode",
                "Swaps in 20 pretend people. Your real list is kept safe and comes back when you turn demo mode off. " +
                    "The demo data is deleted then.",
            ),
            GuideItem(
                "Time travel",
                "Only in demo mode. +1 day, +1 week, +1 month and Next batch day let you see breaks end and new names arrive " +
                    "without waiting.",
            ),
            GuideItem("Reset demo", "Starts the demo over from the beginning."),
            GuideItem(
                "Open test tools",
                "A behind-the-scenes view of the swipe deck, the wheel, cooldowns and reminders, for checking that " +
                    "everything works.",
            ),
        ),
    ),
    GuideSection(
        title = "Welcome tour",
        summary = "The quick walkthrough from the first launch",
        target = GuideTarget.TOUR,
        buttonLabel = "Start the tour",
        items = listOf(
            GuideItem(
                "Welcome tour",
                "Six short pages showing how swiping, the wheel and check-ins work. It opens by itself the first time the " +
                    "app runs after it's installed, and you can replay it any time.",
            ),
        ),
    ),
    GuideSection(
        title = "Privacy & permissions",
        summary = "What the app can and can't do",
        items = listOf(
            GuideItem(
                "Contacts (read only)",
                "Used to show names, numbers and photos. Reach Out never adds, changes or deletes anything in your contacts.",
            ),
            GuideItem("Notifications", "Used only for reminders. You can turn them off in your phone's settings."),
            GuideItem(
                "Run at startup",
                "Lets reminders be planned again after your phone restarts. It doesn't show a prompt.",
            ),
            GuideItem(
                "Your data",
                "Everything stays inside the app on your phone. Call, Text and Share open your own apps, " +
                    "and nothing is sent unless you send it.",
            ),
        ),
    ),
)

/** The How-to guide page. Sections open and close; searching opens every match. */
internal fun LazyListScope.guidePage(
    query: String,
    onQuery: (String) -> Unit,
    expanded: Set<Int>,
    onToggle: (Int) -> Unit,
    onExpandAll: (Boolean) -> Unit,
    onOpen: (GuideTarget) -> Unit,
) {
    item {
        Text(
            "What every setting does, in plain words. Tap a section to open it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search the guide") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val q = query.trim()
    val shown: List<Pair<Int, List<GuideItem>>> = GUIDE.mapIndexedNotNull { i, section ->
        if (q.isEmpty()) {
            i to section.items
        } else {
            val hits = if (section.title.contains(q, ignoreCase = true)) {
                section.items
            } else {
                section.items.filter { it.name.contains(q, ignoreCase = true) || it.text.contains(q, ignoreCase = true) }
            }
            if (hits.isEmpty()) null else i to hits
        }
    }

    if (q.isEmpty()) {
        item {
            val allOpen = expanded.size == GUIDE.size
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onExpandAll(!allOpen) }) { Text(if (allOpen) "Close all" else "Open all") }
            }
        }
    }
    if (shown.isEmpty()) {
        item {
            Text(
                "Nothing matches \"$q\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    shown.forEach { (i, entries) ->
        val section = GUIDE[i]
        item(key = "guide-$i") {
            GuideCard(
                section = section,
                entries = entries,
                open = q.isNotEmpty() || i in expanded,
                onToggle = { onToggle(i) },
                onOpen = section.target?.let { target -> { onOpen(target) } },
            )
        }
    }
}

@Composable
private fun GuideCard(
    section: GuideSection,
    entries: List<GuideItem>,
    open: Boolean,
    onToggle: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (open) "Close section" else "Open section", onClick = onToggle)
                    .semantics { stateDescription = if (open) "Open" else "Closed" }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        section.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (open) "−" else "+",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (open) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    entries.forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                entry.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (onOpen != null) {
                        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(section.buttonLabel) }
                    }
                }
            }
        }
    }
}
