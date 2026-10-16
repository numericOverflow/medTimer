package com.futsch1.medtimer.schedulertests

import com.futsch1.medtimer.core.domain.model.DefaultTakenTime
import com.futsch1.medtimer.feature.reminders.api.scheduling.resolveTakenTime
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class TakenTimeResolutionTest {

    private data class Case(val default: DefaultTakenTime, val reminded: Long, val now: Long, val expected: Long)

    @Test
    fun `should resolve the taken time for every setting`() {
        listOf(
            // Logged late: the setting decides
            Case(DefaultTakenTime.NOW, reminded = 100, now = 400, expected = 400),
            Case(DefaultTakenTime.SCHEDULED, reminded = 100, now = 400, expected = 100),
            // Taken early: never a time in the future
            Case(DefaultTakenTime.NOW, reminded = 500, now = 400, expected = 400),
            Case(DefaultTakenTime.SCHEDULED, reminded = 500, now = 400, expected = 400),
            // Taken on time
            Case(DefaultTakenTime.SCHEDULED, reminded = 400, now = 400, expected = 400),
        ).forEach { case ->
            assertEquals(
                Instant.ofEpochSecond(case.expected),
                resolveTakenTime(case.default, Instant.ofEpochSecond(case.reminded), Instant.ofEpochSecond(case.now)),
                case.toString(),
            )
        }
    }
}
