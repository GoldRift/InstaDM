package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.calendarDaysBetween
import com.goldrift.instadm.ui.chat.formatInboxTime
import java.util.Calendar
import java.util.Locale
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

    @Test
    fun handlesDstSpringForwardAndFallBackDays() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        fun nyAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
            Calendar.getInstance(newYork).apply {
                clear()
                set(year, month - 1, day, hour, minute, 0)
            }.timeInMillis

        // 2026-03-08 is a 23-hour spring-forward day in America/New_York.
        val springDayBeforeJump = nyAt(2026, 3, 8, 1, 30)
        val springDayAfterJump = nyAt(2026, 3, 8, 10, 0)
        val dayAfterSpring = nyAt(2026, 3, 9, 0, 10)
        assertEquals(0L, calendarDaysBetween(springDayBeforeJump, springDayAfterJump, newYork))
        assertEquals(1L, calendarDaysBetween(springDayAfterJump, dayAfterSpring, newYork))
        assertEquals(1L, calendarDaysBetween(springDayBeforeJump, dayAfterSpring, newYork))

        // 2026-11-01 is a 25-hour fall-back day in America/New_York.
        val fallDayEarly = nyAt(2026, 11, 1, 0, 30)
        val fallDayLate = nyAt(2026, 11, 1, 23, 30)
        val dayAfterFall = nyAt(2026, 11, 2, 0, 10)
        assertEquals(0L, calendarDaysBetween(fallDayEarly, fallDayLate, newYork))
        assertEquals(1L, calendarDaysBetween(fallDayEarly, dayAfterFall, newYork))
        assertEquals(1L, calendarDaysBetween(fallDayLate, dayAfterFall, newYork))
    }

    @Test
    fun formatInboxTimeShowsTimeTodayAndMonthDayEarlier() {
        val now = at(2026, 10, 21, 18, 0)
        val sameDay = at(2026, 10, 21, 9, 5)
        val yesterday = at(2026, 10, 20, 23, 55)

        assertEquals("9:05 AM", formatInboxTime(sameDay, now, zone, Locale.US))
        assertEquals("Oct 20", formatInboxTime(yesterday, now, zone, Locale.US))
        assertEquals("", formatInboxTime(0L, now, zone, Locale.US))
    }
}
