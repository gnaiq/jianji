package com.example.jianji.utils

import com.example.jianji.data.RecurringFrequency
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class RecurringScheduleTest {
    @Test fun monthlyClampsDay31ToMonthLength() {
        // 2026-04 只有 30 天，dayOfMonth=31 应钳制为 30（修复 P1-2b 硬截断到 28）
        val now = LocalDateTime.of(2026, 4, 15, 12, 0)
        val r = computeRecurringNextRun(RecurringFrequency.MONTHLY, 31, 1, 1, now)
        assertEquals(LocalDateTime.of(2026, 4, 30, 0, 0), r)
    }

    @Test fun monthlyPastDaySkipsToNextMonth() {
        // 当月 5 号已过（now=4-15），应跳到下个月 5 号
        val now = LocalDateTime.of(2026, 4, 15, 12, 0)
        val r = computeRecurringNextRun(RecurringFrequency.MONTHLY, 5, 1, 1, now)
        assertEquals(LocalDateTime.of(2026, 5, 5, 0, 0), r)
    }

    @Test fun weeklyHonorsDayOfWeek() {
        // 目标周一(1)：取 now 所在周一；若已过去则顺延到下一个周一
        val now = LocalDateTime.of(2026, 4, 15, 12, 0)
        val monday = now.toLocalDate().with(DayOfWeek.MONDAY)
        val expected = if (monday.isBefore(now.toLocalDate())) monday.plusWeeks(1) else monday
        val r = computeRecurringNextRun(RecurringFrequency.WEEKLY, 1, 1, 1, now)
        assertEquals(expected.atStartOfDay(), r)
    }

    @Test fun dailyAdvancesOneDay() {
        val now = LocalDateTime.of(2026, 4, 15, 12, 0)
        val r = computeRecurringNextRun(RecurringFrequency.DAILY, 1, 1, 1, now)
        assertEquals(LocalDateTime.of(2026, 4, 16, 0, 0), r)
    }
}
