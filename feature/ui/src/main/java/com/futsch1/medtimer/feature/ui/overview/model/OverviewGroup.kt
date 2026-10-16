package com.futsch1.medtimer.feature.ui.overview.model

import com.futsch1.medtimer.feature.reminders.api.scheduling.groupBySameTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/** The events of one day that share one reminder time, shown under a single time header. */
data class OverviewGroup(val timestamp: Long, val events: ImmutableList<OverviewEvent>)

/**
 * Groups the already sorted [events] by their reminder time. Without [combineNotifications] every
 * event forms its own group, matching the separate notification it gets.
 */
fun groupOverviewEvents(events: List<OverviewEvent>, combineNotifications: Boolean): ImmutableList<OverviewGroup> {
    val groups = if (combineNotifications) groupBySameTime(events) { it.timestamp } else events.map(::listOf)
    return groups.map { OverviewGroup(it.first().timestamp, it.toPersistentList()) }.toPersistentList()
}
