package com.futsch1.medtimer.feature.ui.overview.model

import com.futsch1.medtimer.feature.ui.overview.TestOverviewEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverviewGroupTest {

    private val nine = TestOverviewEvent(id = 1, timestamp = 32_400, medicineName = "A")
    private val nineToo = TestOverviewEvent(id = 2, timestamp = 32_400, medicineName = "B")
    private val ten = TestOverviewEvent(id = 3, timestamp = 36_000, medicineName = "C")

    @Test
    fun `should put events of the same time into one group when notifications are combined`() {
        val groups = groupOverviewEvents(listOf(nine, nineToo, ten), combineNotifications = true)

        assertEquals(listOf(32_400L, 36_000L), groups.map { it.timestamp })
        assertEquals(listOf(listOf(1, 2), listOf(3)), groups.map { group -> group.events.map { it.id } })
    }

    @Test
    fun `should give every event its own group when notifications are not combined`() {
        val groups = groupOverviewEvents(listOf(nine, nineToo, ten), combineNotifications = false)

        assertEquals(listOf(32_400L, 32_400L, 36_000L), groups.map { it.timestamp })
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), groups.map { group -> group.events.map { it.id } })
    }

    @Test
    fun `should return no groups for a day without events`() {
        assertTrue(groupOverviewEvents(emptyList(), combineNotifications = true).isEmpty())
    }

    @Test
    fun `should move only fully resolved groups into the resolved section`() {
        val taken = TestOverviewEvent(id = 4, timestamp = 28_800, medicineName = "D", state = OverviewState.TAKEN)
        val skipped = TestOverviewEvent(id = 5, timestamp = 32_400, medicineName = "E", state = OverviewState.SKIPPED)
        val groups = groupOverviewEvents(listOf(taken, nine, nineToo, skipped, ten), combineNotifications = true)

        val sections = OverviewDaySections.of(groups)

        // The 09:00 group is part-resolved, so it stays open and in one piece
        assertEquals(listOf(listOf(1, 2, 5), listOf(3)), sections.open.map { group -> group.events.map { it.id } })
        assertEquals(listOf(listOf(4)), sections.resolved.map { group -> group.events.map { it.id } })
        assertEquals(1, sections.resolvedEventCount)
    }

    @Test
    fun `should treat taken and skipped as resolved and everything else as open`() {
        assertEquals(
            setOf(OverviewState.TAKEN, OverviewState.SKIPPED),
            OverviewState.entries.filter { it.isResolved }.toSet(),
        )
    }
}
