package com.futsch1.medtimer.feature.ui.overview.actions

import androidx.fragment.app.FragmentActivity
import com.futsch1.medtimer.core.common.time.TimeAccess
import com.futsch1.medtimer.core.datastore.PersistentDataDataSource
import com.futsch1.medtimer.core.datastore.PreferencesDataSource
import com.futsch1.medtimer.core.domain.model.Medicine
import com.futsch1.medtimer.core.domain.model.Reminder
import com.futsch1.medtimer.core.domain.model.ReminderEvent
import com.futsch1.medtimer.core.domain.model.ScheduledReminder
import com.futsch1.medtimer.core.domain.model.UserPreferences
import com.futsch1.medtimer.feature.reminders.api.command.ReminderCommandBus
import com.futsch1.medtimer.feature.ui.overview.model.PastReminderEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant

/** Coverage for Taken at: one picked time applies to every visited event, already raised or not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class ActionsVisitorTakenAtTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val reminderEventCreator: ReminderEventCreator = mock()
    private val preferencesDataSource: PreferencesDataSource = mock()
    private val persistentDataDataSource: PersistentDataDataSource = mock()
    private val timeAccess: TimeAccess = mock()
    private val commandBus: ReminderCommandBus = mock()
    private val takenTimePicker: TakenTimePicker = mock()

    private val now = Instant.ofEpochSecond(100_000)
    private val pickedTime = Instant.ofEpochSecond(90_000)

    private lateinit var activity: FragmentActivity
    private lateinit var actionsVisitor: ActionsVisitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        whenever(preferencesDataSource.preferences).thenReturn(MutableStateFlow(UserPreferences.default()))
        whenever(persistentDataDataSource.getPendingLocationSnoozes()).thenReturn(emptyList())
        whenever(timeAccess.now()).thenReturn(now)

        actionsVisitor = ActionsVisitor(
            mock(),
            mock(),
            mock(),
            mock(),
            activity,
            reminderEventCreator,
            preferencesDataSource,
            commandBus,
            takenTimePicker,
            timeAccess,
            CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onePickedTimeMarksEveryVisitedEventTaken() {
        // The picker opens on the current time, and the user confirms an earlier one.
        doAnswer { it.getArgument<(Instant) -> Unit>(1)(pickedTime) }.whenever(takenTimePicker).pick(eq(now), any())
        val scheduledReminder = ScheduledReminder(Medicine.default(), Reminder.default(), Instant.ofEpochSecond(95_000))
        stubCreatedEvent(scheduledReminder, reminderEventId = 3)

        actionsVisitor.startVisit(Button.TAKEN_AT).use {
            actionsVisitor.visit(pastEvent(1))
            actionsVisitor.visit(pastEvent(2))
            actionsVisitor.visit(scheduledReminder)
        }
        shadowOf(activity.mainLooper).idle()

        verifyBlocking(commandBus) { markReminderEvents(listOf(3, 1, 2), ReminderEvent.ReminderStatus.TAKEN, pickedTime) }
    }

    @Test
    fun cancellingThePickerMarksNothing() {
        actionsVisitor.startVisit(Button.TAKEN_AT).use {
            actionsVisitor.visit(pastEvent(1))
        }
        shadowOf(activity.mainLooper).idle()

        verifyBlocking(commandBus, never()) { markReminderEvents(any(), any(), anyOrNull()) }
    }

    private fun stubCreatedEvent(scheduledReminder: ScheduledReminder, reminderEventId: Int) {
        reminderEventCreator.stub {
            onBlocking { getOrCreateReminderEvent(scheduledReminder, scheduledReminder.timestamp.epochSecond) }
                .thenReturn(ReminderEvent.default().copy(reminderEventId = reminderEventId))
        }
    }

    private fun pastEvent(id: Int) = PastReminderEvent(
        preferencesDataSource,
        persistentDataDataSource,
        timeAccess,
        ReminderEvent.default().copy(reminderEventId = id, reminderId = id, remindedTimestamp = Instant.ofEpochSecond(95_000)),
    )
}
