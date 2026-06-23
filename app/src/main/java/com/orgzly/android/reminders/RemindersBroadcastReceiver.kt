package com.orgzly.android.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cc.alensiljak.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.AppIntent
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.logs.AppLogsRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.util.userFriendlyPeriod
import com.orgzly.android.util.LogMajorEvents
import com.orgzly.android.util.LogUtils
import com.orgzly.android.util.async
import com.orgzly.org.datetime.OrgDateTime
import org.joda.time.DateTime
import javax.inject.Inject

class RemindersBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var dataRepository: DataRepository

    @Inject
    lateinit var appLogs: AppLogsRepository

    @Inject
    lateinit var remindersScheduler: RemindersScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                AppIntent.ACTION_REMINDER_DATA_CHANGED,
                AppIntent.ACTION_REMINDER_TRIGGERED,
                AppIntent.ACTION_REMINDER_SNOOZE_ENDED,
                AppIntent.ACTION_SHOW_PENDING_REMINDERS)) {
            return
        }

        App.appComponent.inject(this)

        if (!anyRemindersEnabled(context, intent)) return

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent)

        async {
            when (intent.action) {

                AppIntent.ACTION_REMINDER_SNOOZE_ENDED -> {
                    intent.extras?.apply {
                        val noteId: Long = getLong(AppIntent.EXTRA_NOTE_ID, 0)
                        val noteTimeType: Int = getInt(AppIntent.EXTRA_NOTE_TIME_TYPE, 0)
                        val timestamp: Long = getLong(AppIntent.EXTRA_SNOOZE_TIMESTAMP, 0)
                        if (noteId > 0) snoozeEnded(context, noteId, noteTimeType, timestamp)
                    }
                }

                AppIntent.ACTION_SHOW_PENDING_REMINDERS -> {
                    // Manual re-trigger: show all notes due today regardless of when
                    // the last automatic alarm ran. Does not touch the alarm schedule.
                    val today = DateTime()
                    val startOfDay = today.withTimeAtStartOfDay()
                    val endOfDay = startOfDay.plusDays(1)
                    val notes = NoteReminders.getNotificationsInWindow(
                        context, dataRepository, startOfDay, endOfDay)
                    showNotifications(context, notes)
                }

                else -> {
                    // Handles: BOOT_COMPLETED, REMINDER_DATA_CHANGED, REMINDER_TRIGGERED
                    val now = DateTime()

                    // Determine when we last ran. Default to start-of-today on first run
                    // so we catch everything due today without flooding with old notes.
                    val lastRunMs = AppPreferences.reminderLastRun(context)
                    val lastRun = if (lastRunMs > 0L) DateTime(lastRunMs)
                                  else now.withTimeAtStartOfDay()

                    // Cancel any pending alarm before we reschedule below.
                    remindersScheduler.cancelAll()

                    // Show notifications for notes whose time falls in (lastRun, now].
                    val toNotify = NoteReminders.getNotificationsInWindow(
                        context, dataRepository, lastRun, now)
                    showNotifications(context, toNotify)

                    // Schedule the alarm for the next upcoming note.
                    val next = NoteReminders.getNextReminder(context, dataRepository, now)
                    scheduleNext(next, now)

                    // Persist the current time so the next run knows where to start.
                    AppPreferences.reminderLastRun(context, now.millis)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers

    private fun showNotifications(context: Context, notes: List<NoteReminder>) {
        if (notes.isEmpty()) {
            if (LogMajorEvents.isEnabled()) {
                appLogs.log(LogMajorEvents.REMINDERS, "No reminders to show")
            }
            return
        }
        val toShow = notes.takeLast(20)
        if (LogMajorEvents.isEnabled()) {
            appLogs.log(
                LogMajorEvents.REMINDERS,
                "Showing ${toShow.size} of ${notes.size} reminder(s)"
            )
        }
        RemindersNotifications.showNotifications(context, toShow, appLogs)
    }

    private fun scheduleNext(next: NoteReminder?, now: DateTime) {
        if (next == null) {
            if (LogMajorEvents.isEnabled()) {
                appLogs.log(LogMajorEvents.REMINDERS, "No upcoming reminders to schedule")
            }
            return
        }

        // getNextReminder guarantees next.runTime > now, so inMs is always positive.
        val inMs = next.runTime.millis - now.millis
        val hasTime = next.payload.orgDateTime.hasTime()

        if (LogMajorEvents.isEnabled()) {
            appLogs.log(
                LogMajorEvents.REMINDERS,
                "Next: \"${next.payload.title}\" at ${next.runTime} " +
                "(in ${inMs.userFriendlyPeriod()}, hasTime=$hasTime)"
            )
        }

        remindersScheduler.scheduleReminder(inMs, hasTime)
    }

    private fun anyRemindersEnabled(context: Context, intent: Intent): Boolean {
        val enabled = AppPreferences.anyNotificationsEnabled(context)
        if (LogMajorEvents.isEnabled()) {
            val status = if (enabled) "accepted" else "ignored — all reminders disabled"
            appLogs.log(LogMajorEvents.REMINDERS, "Intent $status: $intent")
        }
        return enabled
    }

    private fun snoozeEnded(context: Context, noteId: Long, noteTimeType: Int, timestamp: Long) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, noteId, timestamp)

        val reminders = mutableListOf<NoteReminder>()

        for (noteTime in dataRepository.times()) {
            if (noteTime.noteId == noteId
                && noteTime.timeType == noteTimeType
                && NoteReminders.isRelevantNoteTime(context, noteTime)) {

                val orgDateTime = OrgDateTime.parse(noteTime.orgTimestampString)
                reminders.add(NoteReminder(
                    DateTime(timestamp),
                    NoteReminderPayload(
                        noteTime.noteId,
                        noteTime.bookId,
                        noteTime.bookName,
                        noteTime.title,
                        noteTime.tags,
                        noteTime.timeType,
                        orgDateTime
                    )
                ))
            }
        }

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Snooze ended, ${reminders.size} note(s)")

        if (reminders.isNotEmpty()) {
            RemindersNotifications.showNotifications(context, reminders, appLogs)
        }
    }

    companion object {
        private val TAG: String = RemindersBroadcastReceiver::class.java.name
    }
}
