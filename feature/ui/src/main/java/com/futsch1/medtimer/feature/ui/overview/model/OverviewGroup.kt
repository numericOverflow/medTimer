package com.futsch1.medtimer.feature.ui.overview.model

import com.futsch1.medtimer.feature.reminders.api.scheduling.groupBySameTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/** The events of one day that share one reminder time, shown under a single time header. */
data class OverviewGroup(val timestamp: Long, val events: ImmutableList<OverviewEvent>) {
    /** Every event is taken or skipped, which includes acknowledged stock events. */
    val isResolved: Boolean get() = events.all { it.state.isResolved }
}

/** Taken or skipped: nothing is left to do for the event. */
val OverviewState.isResolved: Boolean get() = this == OverviewState.TAKEN || this == OverviewState.SKIPPED

/**
 * The groups of one day in the order the overview shows them: the groups with work left first, and
 * the fully resolved groups apart. A part-resolved group stays with the open ones, so that one time
 * never splits across two cards.
 */
data class OverviewDaySections(val open: ImmutableList<OverviewGroup>, val resolved: ImmutableList<OverviewGroup>) {
    val resolvedEventCount: Int get() = resolved.sumOf { it.events.size }

    companion object {
        fun of(groups: List<OverviewGroup>): OverviewDaySections {
            val (resolved, open) = groups.partition { it.isResolved }
            return OverviewDaySections(open.toPersistentList(), resolved.toPersistentList())
        }
    }
}

/**
 * Groups the already sorted [events] by their reminder time. Without [combineNotifications] every
 * event forms its own group, matching the separate notification it gets.
 */
fun groupOverviewEvents(events: List<OverviewEvent>, combineNotifications: Boolean): ImmutableList<OverviewGroup> {
    val groups = if (combineNotifications) groupBySameTime(events) { it.timestamp } else events.map(::listOf)
    return groups.map { OverviewGroup(it.first().timestamp, it.toPersistentList()) }.toPersistentList()
}
