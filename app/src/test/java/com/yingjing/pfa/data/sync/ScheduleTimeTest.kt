package com.yingjing.pfa.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ScheduleTimeTest {

    private val zone = ZoneOffset.UTC
    private val hourMs = 3_600_000L

    private fun ms(y: Int, mo: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDate.of(y, mo, d).atTime(h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun laterToday_returnsDelayWithinToday() {
        val now = ms(2026, 1, 10, 8) // 08:00 → 到 09:00
        assertEquals(1 * hourMs, ScheduleTime.initialDelayMillis(now, 9, zone))
    }

    @Test
    fun alreadyPassedToday_rollsToTomorrow() {
        val now = ms(2026, 1, 10, 10) // 10:00 → 明天 09:00
        assertEquals(23 * hourMs, ScheduleTime.initialDelayMillis(now, 9, zone))
    }

    @Test
    fun exactlyOnHour_rollsToNextDay() {
        val now = ms(2026, 1, 10, 9) // 恰好 09:00（不早于 now）→ 明天
        assertEquals(24 * hourMs, ScheduleTime.initialDelayMillis(now, 9, zone))
    }

    @Test
    fun hourClampedToValidRange() {
        val now = ms(2026, 1, 10, 8)
        // hour=25 裁剪为 23 → 到今天 23:00 = 15h
        assertEquals(15 * hourMs, ScheduleTime.initialDelayMillis(now, 25, zone))
    }
}
