package com.futsch1.medtimer.feature.ui.overview

import com.futsch1.medtimer.core.domain.model.OverviewFilter
import org.junit.Test
import kotlin.test.assertEquals

class ResolvedSectionFilterTest {

    @Test
    fun `should open the resolved section only for filters to resolved doses`() {
        data class Case(val filters: Set<OverviewFilter>, val expected: Boolean)
        listOf(
            Case(emptySet(), false),
            Case(setOf(OverviewFilter.TAKEN), true),
            Case(setOf(OverviewFilter.SKIPPED), true),
            Case(setOf(OverviewFilter.TAKEN, OverviewFilter.SKIPPED), true),
            Case(setOf(OverviewFilter.TAKEN, OverviewFilter.RAISED), false),
            Case(setOf(OverviewFilter.SCHEDULED), false),
        ).forEach { (filters, expected) ->
            assertEquals(expected, showsOnlyResolved(filters), filters.toString())
        }
    }
}
