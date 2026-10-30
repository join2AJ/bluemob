package com.bluemob.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DayLabelTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance().apply { set(y, m - 1, d, h, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private val now = at(2026, 10, 30, 22)

    @Test fun messagesFromOtherDaysGetTheirOwnLabel() {
        assertEquals("Today", DayLabel.of(at(2026, 10, 30, 8), now))
        assertEquals("Yesterday", DayLabel.of(at(2026, 10, 29, 23), now))
        // Earlier this week: the weekday; earlier this year: day and month; other years: with the year.
        assertTrue(DayLabel.of(at(2026, 10, 27), now).let { it != "Today" && it != "Yesterday" && !it.any(Char::isDigit) })
        assertTrue(DayLabel.of(at(2026, 9, 2), now).contains("2"))
        assertTrue(DayLabel.of(at(2025, 9, 2), now).contains("2025"))
    }

    @Test fun sameDayIsByCalendarDayNotBy24Hours() {
        assertTrue(DayLabel.sameDay(at(2026, 10, 30, 0), at(2026, 10, 30, 23)))
        assertFalse(DayLabel.sameDay(at(2026, 10, 29, 23), at(2026, 10, 30, 1)))
    }

    @Test fun oneMemberIsSingular() {
        assertEquals("1 member", DayLabel.people(1))
        assertEquals("5 members", DayLabel.people(5))
    }
}
