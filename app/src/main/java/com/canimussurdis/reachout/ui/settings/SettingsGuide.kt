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
        summary = "How ReachOut works, in one minute",
        items = listOf(
            GuideItem(
                "Add people",
                "The app opens on the wheel. Tap Add people (or + Add more people) whenever you like and choose how: " +
                    "Add all contacts (swipe through them one by one), or Pick from a list or a file. If anyone is waiting " +
                    "to be swiped on, Swipe (N waiting) shows just them. Your contacts are only read when you choose.",
            ),
            GuideItem(
                "Swipe",
                "Swipe right to keep someone on your list, or left to pass. A Continue button appears after 3 swipes, " +
                    "but you can keep swiping as long as you like. Back returns to the wheel.",
            ),
            GuideItem(
                "How often",
                "Below each card are four choices for how often that person comes up: Weekly (about every week), " +
                    "Monthly (about once a month), Few months (every 2 to 4 months) or Surprise me (your usual break from " +
                    "Wheel → Break after you connect). It starts on Surprise me and goes back to it after every swipe, " +
                    "so only the person you keep gets your choice. Passing ignores it. Change it later in Contacts, where " +
                    "there's also a Custom choice for exact days.",
            ),
            GuideItem(
                "The wheel",
                "On your batch day (Sunday unless you change it), up to 12 random people from your list go on the wheel. " +
                    "People on a break are left off. Tap Spin to let it choose.",
            ),
            GuideItem(
                "Your pick",
                "Call opens your dialer, Text opens your messages app with your message filled in, and Share lets you send " +
                    "the message through any app. Snooze reminds you again in an hour, or puts them back in the pool: off this " +
                    "week's wheel, ready to be drawn again from the next batch day. " +
                    "The box stays open until you choose one, and opens again whenever you open the app.",
            ),
            GuideItem(
                "Snooze (once per spin)",
                "\"Remind me in 1 hour\" closes the box for now and pauses spinning. The next time you open the app, " +
                    "or tap the reminder, their box opens again with Snooze greyed out, so it's Call, Text or Share.",
            ),
            GuideItem(
                "Did you connect?",
                "When you come back to the app, it asks whether you connected. Yes earns a burst of confetti and gives that person a break before they " +
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
        summary = "Who's on your list, resting, passed or ignored",
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
                "Everyone you kept, with how often each one comes up (Weekly, Monthly, 2–4 months, Surprise or Custom). " +
                    "Tap someone to change it. Custom is only here, not on the swipe screen: choose it, then set Shortest " +
                    "and Longest for that person (the app picks a random break between them each time you connect). " +
                    "Remove from list takes someone off your list and the wheel. The next time you open the app, you'll " +
                    "swipe on them again to keep or pass.",
            ),
            GuideItem(
                "Passed",
                "People you swiped left on. Restore brings them back, and the next time you open the app you'll swipe " +
                    "on them again.",
            ),
            GuideItem(
                "Remove several from your list",
                "Tick as many people as you like (Search and Select all help), then tap Remove. You'll be asked " +
                    "\"Are you sure?\" first. They come off your list and the wheel, and you'll swipe on them again " +
                    "the next time you open the app.",
            ),
            GuideItem(
                "Ignore contacts",
                "Tick people you never want to see as swipe cards, such as businesses or old numbers. They're also " +
                    "left out of imports. Anyone on your list comes off it. You'll be asked \"Are you sure?\" first. " +
                    "Swiping itself doesn't change.",
            ),
            GuideItem(
                "Ignored",
                "Everyone you've ignored. Un-ignore brings them back, and the next time you open the app you'll swipe " +
                    "on them again.",
            ),
        ),
    ),
    GuideSection(
        title = "Import contacts",
        summary = "Pick people to swipe on, from your phone or a file",
        target = GuideTarget.IMPORT,
        buttonLabel = "Open Import contacts",
        items = listOf(
            GuideItem(
                "Pick several at once",
                "Tick people from your phone's contacts, then tap Swipe on. Their swipe cards open next, so you keep or " +
                    "pass on each one, then you're back at the wheel. Anyone you don't get to stays waiting: the app opens on " +
                    "their cards next time (after any picked person on the wheel). Search, Select all and Clear help with long lists.",
            ),
            GuideItem(
                "Add everyone",
                "Opens swipe cards for everyone in your phone you haven't swiped on yet. You'll be asked to confirm first.",
            ),
            GuideItem(
                "From a contacts file (.vcf)",
                "Choose a contacts file exported from Google Contacts, iCloud, Outlook or another phone, untick anyone you " +
                    "don't want, then swipe on the rest. This works even without contacts access.",
            ),
            GuideItem(
                "Good to know",
                "You can also get here from the wheel: Add people → Pick from a list or a file. The first two options ask " +
                    "for contacts access (read only) the first time. People already on your list, or ones you passed on, " +
                    "are skipped. Your phone's contacts are never changed.",
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
        summary = "How the wheel looks, when names change, break lengths, and what happens after each pick",
        target = GuideTarget.WHEEL,
        buttonLabel = "Open Wheel",
        items = listOf(
            GuideItem(
                "Wheel style",
                "Round wheel (the default) is a prize wheel tilted back like it's on a table; the name under the pointer " +
                    "at the top is the pick. " +
                    "Big wheel is a tall wheel that rolls downward, like on a TV game show: names run across it, and the " +
                    "one between the two pointers is the pick. Tap it, swipe it or press Spin. Both work exactly the same " +
                    "way: the same names, Snooze, reminders and choices below. With Reveal picture, the Big Wheel's " +
                    "uncovered names show the hidden picture behind it.",
            ),
            GuideItem(
                "New names each week on",
                "The day a fresh set of up to 12 names is drawn. The default is Sunday. If the wheel empties before then, " +
                    "it's refilled straight away.",
            ),
            GuideItem(
                "Break after you connect",
                "After you answer Yes, that person rests for a random time between Shortest and Longest before they can " +
                    "come up again. The default is 1 week to 6 months. This is what Surprise me uses. Change it for some " +
                    "people with How often when you keep them, or in Contacts.",
            ),
            GuideItem(
                "When you reach someone",
                "New name (the default): a random new person takes their spot, so the wheel stays full. " +
                    "Reveal picture: their slice uncovers part of a hidden picture instead. Reach everyone to see the whole thing.",
            ),
            GuideItem(
                "When you answer \"Not yet\"",
                "Back to pool (the default): they leave this week's wheel without a break and won't come back onto it, " +
                    "then can be drawn again from the next batch day. With New name, someone else takes their spot. " +
                    "Keep on wheel: they stay on this week's wheel.",
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
                "Shows only if your phone is blocking ReachOut's notifications. Open notification settings takes you " +
                    "straight there.",
            ),
            GuideItem(
                "Keep it until you reach out",
                "On to start with. The reminder stays in your notifications until you call, text or share your pick, " +
                    "from the reminder or from the app. Clear all skips it, and on the lock screen it can't be swiped " +
                    "away. On Android 14 and newer you can still swipe it away when the phone is unlocked, so it comes " +
                    "right back. The reminder has Call, Text and Snooze 1 hr buttons (Spin the wheel if nobody's " +
                    "picked yet). Snooze hides it for an hour, once per pick. The lock screen shows just the reminder, " +
                    "without the buttons, when your phone hides notification details there. Turn this off to swipe " +
                    "reminders away like any other.",
            ),
            GuideItem("How often", "Daily (the default), Weekly, Every 2 weeks, Monthly or Yearly."),
            GuideItem(
                "On these days",
                "Daily: all seven days are on to start with. Tap a day to skip it (for example, weekends). " +
                    "Weekly and Every 2 weeks: tap as many days as you like. You'll get a nudge on each one (every other " +
                    "week for Every 2 weeks). At least one day always stays picked.",
            ),
            GuideItem(
                "In these months",
                "Yearly: tap the months you want. You'll get one nudge in each, on the first chosen day of that month " +
                    "(for example, the first Sunday). At least one month stays picked.",
            ),
            GuideItem(
                "On this date",
                "Monthly: tap the date and type a day from 1 to 31, for example the 15th. You'll get a nudge on that " +
                    "date every month. In shorter months (like the 31st in April), it comes on the last day.",
            ),
            GuideItem(
                "On",
                "Yearly: which day of the week, for example the first Sunday of each month you pick.",
            ),
            GuideItem(
                "Only between",
                "The hours a reminder can arrive. Tap From or Until and type a time; the window must be at least an hour long. The default is " +
                    "5 pm to 9 pm. Each reminder lands at a random time inside it. 1-hour snoozes also wait for this window.",
            ),
            GuideItem(
                "Different hours on some days",
                "Turn this on to give some days their own hours, for example a later start on weekends. Each day " +
                    "of the week is listed: tap its From or Until to change it, and Use usual hours to undo. Days you " +
                    "leave alone keep your usual hours. Snoozes wait for that day's hours too.",
            ),
            GuideItem("Next reminder", "When the next nudge is planned."),
            GuideItem(
                "Battery tip",
                "Samsung and OnePlus phones can delay reminders to save battery. Set Settings → Apps → ReachOut → " +
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
                "Blank plus five ready-made messages. Tap one to use it for Text and Share (and Text on the home screen " +
                    "widget). The default is Blank, which opens them with nothing typed, so you write your own each time.",
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
        summary = "Theme, color palette, text size, motion and sound",
        target = GuideTarget.DISPLAY,
        buttonLabel = "Open Display",
        items = listOf(
            GuideItem("Theme", "Follow phone matches your phone's light or dark mode. Light and Dark keep it one way."),
            GuideItem(
                "Color palette",
                "Terracotta (the default), Neutral, Ocean, Lavender, Forest or Sunshine. " +
                    "Each has a light and a dark version, so it works with " +
                    "any Theme choice. The three dots show the background, button and accent colors.",
            ),
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
                "Only in demo mode. Tap Pretend time to type any hour and minute on the pretend day. +1 day, +1 week, " +
                    "+1 month and Next batch day let you see breaks end and new names arrive without waiting. " +
                    "Reminders follow the pretend clock, and jumping past a planned reminder delivers it.",
            ),
            GuideItem("Reset demo", "Starts the demo over from the beginning."),
            GuideItem("Send a test reminder now", "Shows a reminder notification straight away, to check notifications work."),
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
            GuideItem("About", "Settings → About shows which version of ReachOut you have, for example 1.00."),
        ),
    ),
    GuideSection(
        title = "Home screen widgets",
        summary = "See who's up without opening the app",
        items = listOf(
            GuideItem(
                "Adding a widget",
                "Press and hold an empty spot on your home screen, tap Widgets, find ReachOut, then drag the one " +
                    "you want onto your home screen. You can resize it by pressing and holding it.",
            ),
            GuideItem(
                "Who's up",
                "Shows who the wheel picked. Tap it to open their box (Call, Text, Share, Snooze). With nobody picked, " +
                    "it says Time to say hi, and Spin the wheel opens the app on the wheel.",
            ),
            GuideItem(
                "Who's up + Call/Text",
                "The same, with Call and Text buttons right on the widget. Text fills in the same message as Text in " +
                    "the app (Blank by default). " +
                    "Tapping either counts as reaching out, just like in the app: the app asks \"Did you connect?\" " +
                    "next time you open it.",
            ),
            GuideItem(
                "Privacy",
                "Widgets show the picked person's name on your home screen. They never read your contacts. " +
                    "To keep names off the home screen, just don't add a widget.",
            ),
        ),
    ),
    GuideSection(
        title = "Privacy & permissions",
        summary = "What the app can and can't do",
        items = listOf(
            GuideItem(
                "Contacts (read only)",
                "Only read when you choose to add people, to show names, numbers and photos. ReachOut never adds, " +
                    "changes or deletes anything in your contacts.",
            ),
            GuideItem("Notifications", "Used only for reminders. You can turn them off in your phone's settings."),
            GuideItem(
                "Run at startup",
                "Lets reminders be planned again after your phone restarts. It doesn't show a prompt.",
            ),
            GuideItem(
                "Your data",
                "Everything stays inside the app on your phone (and in your phone's own encrypted Google backup, " +
                    "if that's on). Call, Text and Share open your own apps, and nothing is sent unless you send it.",
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
