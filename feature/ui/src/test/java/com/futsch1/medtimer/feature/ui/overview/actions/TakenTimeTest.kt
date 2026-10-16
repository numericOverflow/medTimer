package com.futsch1.medtimer.feature.ui.overview.actions

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TakenTimeTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 5, 28)
    private val now = today.atTime(12, 0).atZone(zone).toInstant()

    @Test
    fun `should accept a time earlier today`() {
        assertEquals(today.atTime(8, 30).atZone(zone).toInstant(), takenTimeOrNull(today, 8 * 60 + 30, zone, now))
    }

    @Test
    fun `should accept a time on an earlier day`() {
        val yesterday = today.minusDays(1)
        assertEquals(yesterday.atTime(22, 0).atZone(zone).toInstant(), takenTimeOrNull(yesterday, 22 * 60, zone, now))
    }

    @Test
    fun `should accept the current minute`() {
        assertEquals(now, takenTimeOrNull(today, 12 * 60, zone, now))
    }

    @Test
    fun `should reject a time later today`() {
        assertNull(takenTimeOrNull(today, 12 * 60 + 1, zone, now))
    }

    @Test
    fun `should reject a time on a later day`() {
        assertNull(takenTimeOrNull(today.plusDays(1), 0, zone, now))
    }
}
