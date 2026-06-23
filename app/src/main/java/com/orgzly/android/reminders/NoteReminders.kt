package com.orgzly.android.reminders

import android.content.Context
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.dao.ReminderTimeDao
import com.orgzly.android.db.dao.ReminderTimeDao.NoteTime
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgDateTimeUtils
import com.orgzly.org.datetime.OrgInterval
import org.joda.time.DateTime

object NoteReminders {

    /**
     * All notes whose effective reminder time falls in [from, to).
     *
     * "Effective time" means:
     *  - the note's own timestamp if it has a time component (e.g. 10:00)
     *  - the configured daily reminder time otherwise (date-only timestamps)
     *  - shifted earlier by the warning period for deadline/event notes
     *
     * Used to collect what to show since the last alarm run.
     */
    fun getNotificationsInWindow(
        context: Context,
        dataRepository: DataRepository,
        from: DateTime,
        to: DateTime
    ): List<NoteReminder> = collect(context, dataRepository) { time ->
        !time.isBefore(from) && time.isBefore(to)
    }

    /**
     * The single next upcoming note with effective time strictly after [after].
     * Returns null if nothing is scheduled ahead.
     *
     * Used to decide when to set the next AlarmManager alarm.
     */
    fun getNextReminder(
        context: Context,
        dataRepository: DataRepository,
        after: DateTime
    ): NoteReminder? = collect(context, dataRepository) { time ->
        time.isAfter(after)
    }.minByOrNull { it.runTime }

    /**
     * Whether this note time entry should produce a reminder at all:
     * its type must be enabled in settings and the note must not be done.
     */
    fun isRelevantNoteTime(context: Context, noteTime: NoteTime): Boolean {
        if (AppPreferences.doneKeywordsSet(context).contains(noteTime.state)) return false
        return AppPreferences.remindersForScheduledEnabled(context)
                && noteTime.timeType == ReminderTimeDao.SCHEDULED_TIME
                || AppPreferences.remindersForDeadlineEnabled(context)
                && noteTime.timeType == ReminderTimeDao.DEADLINE_TIME
                || AppPreferences.remindersForEventsEnabled(context)
                && noteTime.timeType == ReminderTimeDao.EVENT_TIME
    }

    // -------------------------------------------------------------------------

    /**
     * Shared collection logic. Iterates all note times, computes each note's
     * effective reminder time via OrgDateTimeUtils (handles date-only offsets
     * and deadline warning periods), then keeps only notes matching [predicate].
     * Result is sorted by time, oldest first.
     */
    private fun collect(
        context: Context,
        dataRepository: DataRepository,
        predicate: (DateTime) -> Boolean
    ): List<NoteReminder> {
        val dailyTime = AppPreferences.reminderDailyTime(context)
        val result = mutableListOf<NoteReminder>()

        for (noteTime in dataRepository.times()) {
            if (!isRelevantNoteTime(context, noteTime)) continue

            val orgDateTime = OrgDateTime.parse(noteTime.orgTimestampString)

            // Deadline and event notes support an optional warning period that
            // shifts the effective reminder time earlier (e.g. "-3d" on a
            // DEADLINE fires three days before the actual due date).
            val warningPeriod: OrgInterval? =
                if (noteTime.timeType == ReminderTimeDao.DEADLINE_TIME
                    || noteTime.timeType == ReminderTimeDao.EVENT_TIME) {
                    (if (orgDateTime.hasDelay()) orgDateTime.delay else null) as? OrgInterval
                } else null

            // getTimesInInterval with from=epoch and to=null returns exactly one
            // element for non-repeating timestamps: the note's effective time
            // with the daily-time offset (for date-only notes) and warning period
            // already applied. useRepeater=false keeps it to a single occurrence.
            val effectiveTime = OrgDateTimeUtils.getTimesInInterval(
                orgDateTime,
                DateTime(0),   // epoch — always before any real note date
                null,
                dailyTime,
                false,         // do not use repeater
                warningPeriod,
                1
            ).firstOrNull() ?: continue

            if (predicate(effectiveTime)) {
                result.add(NoteReminder(
                    effectiveTime,
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

        result.sortWith { a, b -> a.runTime.compareTo(b.runTime) }
        return result
    }
}
