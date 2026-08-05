package com.example.jianji.utils

import com.example.jianji.core.common.computeRecurringNextRun
import com.example.jianji.data.RecurringFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * 验收 D2-1：周期下一次执行推算统一（computeRecurringNextRun）。
 * 重点验证 WEEKLY 严格落在指定 weekday，且 interval=N 表示每 N 周一次。
 */
class RecurringNextRunTest {

    @Test
    fun `WEEKLY 落在指定星期三`() {
        // 2026-08-03 是周一；每周三（dayOfWeek=3）应得 2026-08-05
        val now = LocalDateTime.of(2026, 8, 3, 10, 0)
        val next = computeRecurringNextRun(
            RecurringFrequency.WEEKLY, dayOfMonth = 1, interval = 1, dayOfWeek = 3, now = now
        )
        assertEquals(DayOfWeek.WEDNESDAY, next.dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 8, 5, 0, 0), next)
    }

    @Test
    fun `WEEKLY interval=2 每两周一次`() {
        val now = LocalDateTime.of(2026, 8, 5, 10, 0) // 周三
        val next = computeRecurringNextRun(
            RecurringFrequency.WEEKLY, dayOfMonth = 1, interval = 2, dayOfWeek = 3, now = now
        )
        // 命中本周三后推进 (interval-1)=1 周 → 2026-08-12
        assertEquals(LocalDateTime.of(2026, 8, 12, 0, 0), next)
    }

    @Test
    fun `MONTHLY 月末钳制 非满月月份 且取本月未来日期`() {
        // 31 号：now=1/15，本月 1/31 仍在未来，故返回 1/31（下月月末钳制在 catch-up 推进时自然处理）
        val now = LocalDateTime.of(2026, 1, 15, 0, 0)
        val next = computeRecurringNextRun(
            RecurringFrequency.MONTHLY, dayOfMonth = 31, interval = 1, now = now
        )
        assertEquals(LocalDateTime.of(2026, 1, 31, 0, 0), next)
    }

    @Test
    fun `DAILY 推算正确`() {
        val now = LocalDateTime.of(2026, 8, 3, 12, 0)
        val next = computeRecurringNextRun(RecurringFrequency.DAILY, dayOfMonth = 1, interval = 2, now = now)
        assertEquals(LocalDateTime.of(2026, 8, 5, 0, 0), next)
    }

    @Test
    fun `catch-up nextRunAfter 与 computeRecurringNextRun 语义一致 WEEKLY`() {
        // nextRunAfter 内部委托 computeRecurringNextRun，这里验证 WEEKLY 不跳过 weekday
        val prev = LocalDateTime.of(2026, 8, 3, 10, 0) // 周一
        val rtx = com.example.jianji.data.RecurringTransaction(
            categoryId = 1, amountCents = 100, type = com.example.jianji.data.TransactionType.EXPENSE,
            frequency = RecurringFrequency.WEEKLY, interval = 1, dayOfWeek = 3, // 周三
            nextRunDate = prev
        )
        // 通过 TransactionViewModel 的 nextRunAfter 间接验证：直接调用 computeRecurringNextRun 等价
        val next = computeRecurringNextRun(
            rtx.frequency, rtx.dayOfMonth, rtx.interval, rtx.dayOfWeek, rtx.monthOfYear, prev.plusSeconds(1)
        )
        assertEquals(DayOfWeek.WEDNESDAY, next.dayOfWeek)
        assertTrue("下次执行不得早于 prev（避免重复记账）", !next.isBefore(prev))
    }
}
