package com.futsch1.medtimer.feature.ui.overview

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.futsch1.medtimer.core.domain.model.OverviewFilter
import com.futsch1.medtimer.core.ui.list.SelectionListController
import com.futsch1.medtimer.core.ui.theme.MedTimerTheme
import com.futsch1.medtimer.feature.ui.overview.model.OverviewEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OverviewFilterVisibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `hidden filters clear the checked filters and shown filters keep them`() {
        data class Case(val showFilters: Boolean, val checked: Set<OverviewFilter>, val expected: Set<OverviewFilter>)
        listOf(
            Case(true, emptySet(), emptySet()),
            Case(true, setOf(OverviewFilter.TAKEN, OverviewFilter.RAISED), setOf(OverviewFilter.TAKEN, OverviewFilter.RAISED)),
            Case(false, emptySet(), emptySet()),
            Case(false, setOf(OverviewFilter.TAKEN), emptySet()),
            Case(false, OverviewFilter.entries.toSet(), emptySet()),
        ).forEach { case ->
            assertEquals(case.expected, filtersForVisibility(case.showFilters, case.checked), case.toString())
        }
    }

    @Test
    fun `shows the filter row when the setting is on`() {
        setScreen(showFilters = true)

        OverviewFilter.entries.forEach { composeTestRule.onNodeWithTag(OverviewTestTags.filter(it)).assertExists() }
    }

    @Test
    fun `hides the filter row when the setting is off`() {
        setScreen(showFilters = false)

        OverviewFilter.entries.forEach { composeTestRule.onNodeWithTag(OverviewTestTags.filter(it)).assertDoesNotExist() }
    }

    private fun setScreen(showFilters: Boolean) {
        val state = MutableOverviewScreenState().apply { this.showFilters = showFilters }
        val selection = SelectionListController<OverviewEvent> { it.id }
        composeTestRule.setContent {
            MedTimerTheme {
                OverviewScreen(
                    state = state,
                    selection = selection,
                    onDaySelected = {},
                    onToggleFilter = {},
                    onToggleResolvedSection = {},
                    onDismissBatteryWarning = {},
                    onDismissExactRemindersWarning = {},
                    onEnableExactReminders = {},
                    onEnterSelectionMode = {},
                    onEventClick = {},
                    onAction = { _, _ -> },
                    onLogManualDose = {},
                    topBarActions = {},
                )
            }
        }
    }
}
