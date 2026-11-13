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
import com.futsch1.medtimer.feature.ui.overview.VariableAmountHandler
import com.futsch1.medtimer.feature.ui.overview.model.PastReminderEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
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
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage for plain Taken on the Overview: it shares the take path of Taken at without a picked time, so doses that
 * ask for the dosage get the amount dialog and an upcoming one gets its event row only on OK.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class ActionsVisitorTakenTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val reminderEventCreator: ReminderEventCreator = mock()
    private val preferencesDataSource: PreferencesDataSource = mock()
    private val persistentDataDataSource: PersistentDataDataSource = mock()
    private val timeAccess: TimeAccess = mock()
    private val commandBus: ReminderCommandBus = mock()
    private val variableAmountHandler: VariableAmountHandler = mock()

    private lateinit var activity: FragmentActivity
    private lateinit var actionsVisitor: ActionsVisitor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        whenever(preferencesDataSource.preferences).thenReturn(MutableStateFlow(UserPreferences.default()))
        whenever(persistentDataDataSource.getPendingLocationSnoozes()).thenReturn(emptyList())
        whenever(timeAccess.now()).thenReturn(Instant.ofEpochSecond(7200))
        whenever(timeAccess.systemZone()).thenReturn(ZoneId.of("Europe/Berlin"))

        actionsVisitor = ActionsVisitor(
            mock(),
            mock(),
            mock(),
            mock(),
            activity,
            reminderEventCreator,
            preferencesDataSource,
            commandBus,
            mock<TakenTimePicker>(),
            timeAccess,
            variableAmountHandler,
            CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun takenOnARaisedRowAskingForTheDosageGoesToTheAmountDialog() {
        taken(pastEvent(1, askForAmount = true))

        assertEquals(listOf(1), amountRequests().map { runBlocking { it.reminderEventId() } })
        verifyBlocking(commandBus, never()) { markReminderEvents(any(), any(), anyOrNull()) }
    }

    @Test
    fun takenOnASkippedRowAskingForTheDosageGoesToTheAmountDialog() {
        taken(pastEvent(1, askForAmount = true, status = ReminderEvent.ReminderStatus.SKIPPED))

        assertEquals(listOf(1), amountRequests().map { runBlocking { it.reminderEventId() } })
        verifyBlocking(commandBus, never()) { markReminderEvents(any(), any(), anyOrNull()) }
    }

    @Test
    fun takenOnAPlainRowMarksItDirectly() {
        taken(pastEvent(1))

        verifyBlocking(commandBus) { markReminderEvents(listOf(1), ReminderEvent.ReminderStatus.TAKEN, null) }
        verifyBlocking(variableAmountHandler, never()) { takeWithAmounts(any(), any(), anyOrNull()) }
    }

    @Test
    fun takenOnAnUpcomingDoseAskingForTheDosageCreatesItsRowOnlyOnOk() {
        val scheduledReminder = ScheduledReminder(
            Medicine.default().copy(name = "Vitamin X 500 mg"),
            Reminder.default().copy(amount = "2", variableAmount = true),
            Instant.ofEpochSecond(3600)
        )
        reminderEventCreator.stub {
            onBlocking { getOrCreateReminderEvent(scheduledReminder, scheduledReminder.timestamp.epochSecond) }
                .thenReturn(ReminderEvent.default().copy(reminderEventId = 3))
        }

        actionsVisitor.startVisit(Button.TAKEN).use {
            actionsVisitor.visit(scheduledReminder)
        }
        idle()

        val request = amountRequests().single()
        assertEquals("Vitamin X 500 mg", request.medicineName)
        assertEquals("2", request.initialAmount)
        verifyBlocking(reminderEventCreator, never()) { getOrCreateReminderEvent(any(), any()) }
        assertNull(shadowOf(activity).nextStartedActivity)

        // The handler calls this only after OK on the amount dialog.
        assertEquals(3, runBlocking { request.reminderEventId() })
        verifyBlocking(commandBus, never()) { markReminderEvents(any(), any(), anyOrNull()) }
    }

    @Test
    fun aMixedSelectionMarksThePlainDosesOnceAndAsksInScreenOrder() {
        actionsVisitor.startVisit(Button.TAKEN).use {
            actionsVisitor.visit(pastEvent(1))
            actionsVisitor.visit(pastEvent(2, askForAmount = true, medicineName = "Medicine A"))
            actionsVisitor.visit(pastEvent(3))
            actionsVisitor.visit(pastEvent(4, askForAmount = true, medicineName = "Medicine B"))
        }
        idle()

        verifyBlocking(commandBus) { markReminderEvents(listOf(1, 3), ReminderEvent.ReminderStatus.TAKEN, null) }
        assertEquals(listOf("Medicine A", "Medicine B"), amountRequests().map { it.medicineName })
    }

    private fun taken(pastReminderEvent: PastReminderEvent) {
        actionsVisitor.startVisit(Button.TAKEN).use {
            actionsVisitor.visit(pastReminderEvent)
        }
        idle()
    }

    private fun amountRequests(): List<VariableAmountHandler.AmountRequest> {
        val captor = argumentCaptor<List<VariableAmountHandler.AmountRequest>>()
        verifyBlocking(variableAmountHandler) { takeWithAmounts(eq(activity), captor.capture(), isNull()) }
        return captor.firstValue
    }

    private fun idle() = shadowOf(activity.mainLooper).idle()

    private fun pastEvent(
        id: Int,
        askForAmount: Boolean = false,
        medicineName: String = "",
        status: ReminderEvent.ReminderStatus = ReminderEvent.ReminderStatus.RAISED,
    ) = PastReminderEvent(
        preferencesDataSource,
        persistentDataDataSource,
        timeAccess,
        ReminderEvent.default().copy(reminderEventId = id, reminderId = id, askForAmount = askForAmount, medicineName = medicineName, status = status),
    )
}
