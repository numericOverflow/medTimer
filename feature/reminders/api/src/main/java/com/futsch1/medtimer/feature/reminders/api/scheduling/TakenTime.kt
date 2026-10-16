package com.futsch1.medtimer.feature.reminders.api.scheduling

import com.futsch1.medtimer.core.domain.model.DefaultTakenTime
import java.time.Instant

/**
 * The time a dose records when it is marked taken without a picked time.
 *
 * For [DefaultTakenTime.SCHEDULED] that is the reminder time, but never later than [now]: a dose
 * taken early would otherwise record a time in the future, and interval and linked reminders
 * derive their next due time from the recorded time.
 */
fun resolveTakenTime(default: DefaultTakenTime, remindedTimestamp: Instant, now: Instant): Instant =
    when (default) {
        DefaultTakenTime.NOW -> now
        DefaultTakenTime.SCHEDULED -> minOf(remindedTimestamp, now)
    }
