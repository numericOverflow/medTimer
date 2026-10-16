package com.futsch1.medtimer.feature.ui.overview.actions

import androidx.fragment.app.FragmentActivity
import com.futsch1.medtimer.core.common.helpers.DatePickerDialogFactory
import com.futsch1.medtimer.core.common.helpers.TimePickerDialogFactory
import com.futsch1.medtimer.core.common.time.TimeAccess
import com.futsch1.medtimer.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Asks for the time a dose was really taken: a date picker, then a time picker. A time in the
 * future is rejected, and the time picker opens again on the current time with a hint, because a
 * future taken time would shift the next due time of interval and linked reminders.
 */
class TakenTimePicker @Inject constructor(
    private val datePickerDialogFactory: DatePickerDialogFactory,
    private val timePickerDialogFactory: TimePickerDialogFactory,
    private val fragmentActivity: FragmentActivity,
    private val timeAccess: TimeAccess,
) {
    /** Opens the pickers on [initial] and hands the confirmed time to [onPicked]. Cancel calls nothing. */
    fun pick(initial: Instant, onPicked: (Instant) -> Unit) {
        val zone = timeAccess.systemZone()
        val initialDateTime = initial.atZone(zone).toLocalDateTime()
        datePickerDialogFactory.create(initialDateTime.toLocalDate()) { daysSinceEpoch ->
            pickTime(LocalDate.ofEpochDay(daysSinceEpoch), initialDateTime.toLocalTime(), titleText = null, onPicked)
        }.show(fragmentActivity.supportFragmentManager, DatePickerDialogFactory.DIALOG_TAG)
    }

    private fun pickTime(date: LocalDate, initialTime: LocalTime, titleText: Int?, onPicked: (Instant) -> Unit) {
        timePickerDialogFactory.create(initialTime, titleText) { minutes ->
            val zone = timeAccess.systemZone()
            val now = timeAccess.now()
            val picked = takenTimeOrNull(date, minutes, zone, now)
            if (picked != null) {
                onPicked(picked)
            } else {
                // A future date cannot hold a valid time, so the retry moves to today.
                val nowDateTime = now.atZone(zone).toLocalDateTime()
                pickTime(minOf(date, nowDateTime.toLocalDate()), nowDateTime.toLocalTime(), R.string.taken_time_in_future, onPicked)
            }
        }.show(fragmentActivity.supportFragmentManager, TimePickerDialogFactory.DIALOG_TAG)
    }
}

/** The instant of [minutes] past midnight on [date], or null when that instant lies after [now]. */
internal fun takenTimeOrNull(date: LocalDate, minutes: Int, zone: ZoneId, now: Instant): Instant? =
    date.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant().takeIf { it <= now }
