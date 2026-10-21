package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.calendarDaysBetween
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDateHeaderTest {
    private val zone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun sameDayIsZeroDays() {
        val morning = at(2026, 10, 21, 8, 30)
        val evening = at(2026, 10, 21, 22, 5)

        assertEquals(0L, calendarDaysBetween(morning, evening, zone))
    }

    @Test
    fun previousDayIsOneDayEvenWithoutTwentyFourHours() {
        val lateYesterday = at(2026, 10, 20, 23, 50)
        val earlyToday = at(2026, 10, 21, 0, 10)

        assertEquals(1L, calendarDaysBetween(lateYesterday, earlyToday, zone))
    }

    @Test
    fun olderDatesReportWholeDays() {
        val older = at(2026, 10, 18, 12, 0)
        val today = at(2026, 10, 21, 9, 0)

        assertEquals(3L, calendarDaysBetween(older, today, zone))
    }

    @Test
    fun futureTimestampsAreNegative() {
        val tomorrow = at(2026, 10, 22, 9, 0)
        val today = at(2026, 10, 21, 9, 0)

        assertEquals(-1L, calendarDaysBetween(tomorrow, today, zone))
    }
}
