package com.canimussurdis.reachout.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.canimussurdis.reachout.MainActivity
import com.canimussurdis.reachout.R
import com.canimussurdis.reachout.data.ReachOutRepository
import com.canimussurdis.reachout.reminders.Reminders

/**
 * The two home screen widgets. Both show who the wheel picked:
 *  - "Who's up" ([PickWidget]): tap to open their box, or to open the wheel and spin.
 *  - "Who's up + Call/Text" ([CallTextWidget]): adds Call and Text buttons.
 *
 * Widgets never read your contacts: they only show the person the app already saved as the pick.
 * They follow the same rules as the app: Call or Text counts as reaching out, and the app asks
 * "Did you connect?" the next time it opens. Text fills in the same message as Text in the app.
 */
object ReachOutWidgets {

    private const val ACTION_CALL = "com.canimussurdis.reachout.widget.CALL"
    private const val ACTION_TEXT = "com.canimussurdis.reachout.widget.TEXT"
    private const val EXTRA_CONTACT = "contact"

    /** Redraws every ReachOut widget on the home screen. Cheap, and safe to call from any thread. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (withCallText in listOf(false, true)) {
            val provider = ComponentName(context, if (withCallText) CallTextWidget::class.java else PickWidget::class.java)
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, withCallText))
        }
    }

    internal fun views(context: Context, withCallText: Boolean): RemoteViews {
        val info = ReachOutRepository.get(context).widgetInfo()
        val pick = info.pick
        val views = RemoteViews(
            context.packageName,
            if (withCallText) R.layout.widget_call_text else R.layout.widget_pick,
        )

        // Two short lines: "Reach out to" + their name, or the app's name + what to do next.
        views.setTextViewText(R.id.widget_label, if (pick != null) "Reach out to" else "ReachOut")
        views.setTextViewText(
            R.id.widget_name,
            when {
                pick != null -> pick.name
                info.hasPeople -> "Time to say hi"
                else -> "Add people to start"
            },
        )
        views.setTextViewText(
            R.id.widget_open,
            if (pick == null && info.hasPeople) "Spin" else "Open",
        )

        // Tapping the widget (or Open / Spin) opens the app: on the pick's box, or on the wheel.
        // The app double-checks the person is still the pick before opening their box.
        val open = PendingIntent.getActivity(
            context,
            if (withCallText) 2001 else 2000,
            Intent(context, MainActivity::class.java)
                .putExtra(Reminders.EXTRA_OPEN, pick?.id ?: Reminders.OPEN_WHEEL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(android.R.id.background, open)
        views.setOnClickPendingIntent(R.id.widget_open, open)

        if (withCallText) {
            val canReach = pick != null && pick.mainNumber != null
            views.setViewVisibility(R.id.widget_actions, if (canReach) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_open, if (canReach) View.GONE else View.VISIBLE)
            if (canReach) {
                views.setOnClickPendingIntent(R.id.widget_call, actionIntent(context, ACTION_CALL, pick!!.id, 2002))
                views.setOnClickPendingIntent(R.id.widget_text, actionIntent(context, ACTION_TEXT, pick.id, 2003))
            }
        }
        return views
    }

    private fun actionIntent(context: Context, action: String, contactId: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, WidgetActionActivity::class.java)
                .setAction(action)
                .putExtra(EXTRA_CONTACT, contactId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Call or Text from the widget: counts as reaching out, then opens the dialer or messages app. */
    internal fun handleAction(activity: Activity, intent: Intent) {
        val repo = ReachOutRepository.get(activity.applicationContext)
        val pick = repo.currentPick()
        val number = pick?.mainNumber
        // Only for the person who's still the pick (the widget may be a moment out of date).
        if (pick == null || number == null || pick.id != intent.getStringExtra(EXTRA_CONTACT)) {
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .putExtra(Reminders.EXTRA_OPEN, Reminders.OPEN_WHEEL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            return
        }
        val out = when (intent.action) {
            ACTION_CALL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            // The same message as Text in the app's box (Settings → Messages). Blank, the default, fills in nothing.
            ACTION_TEXT -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
                val message = repo.settings.message
                if (message.isNotBlank()) putExtra("sms_body", message)
            }
            else -> return
        }
        try {
            activity.startActivity(out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            repo.markReachedOut(pick.id)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "No app found to do that.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** The "Who's up" widget. Declared in AndroidManifest.xml. */
class PickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, ReachOutWidgets.views(context, withCallText = false))
    }
}

/** The "Who's up + Call/Text" widget. Declared in AndroidManifest.xml. */
class CallTextWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, ReachOutWidgets.views(context, withCallText = true))
    }
}

/**
 * An invisible screen that runs for a split second when Call or Text is tapped on the widget:
 * it records that you reached out, opens the dialer or messages app, and closes.
 * Not available to other apps (exported="false" in AndroidManifest.xml).
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReachOutWidgets.handleAction(this, intent)
        finish()
    }
}
