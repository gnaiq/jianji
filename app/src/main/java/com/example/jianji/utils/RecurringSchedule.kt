package com.example.jianji.utils

import com.example.jianji.data.RecurringFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.min

/**
 * 计算周期交易的下次执行时间（供预览与保存复用，避免两处逻辑不一致）。
 * 纯函数，依赖传入的 [now] 便于测试与跨时区一致。
 */
fun computeRecurringNextRun(
    freq: RecurringFrequency,
    dayOfMonth: Int,
    interval: Int,
    dayOfWeek: Int = 1,
    monthOfYear: Int = 1,
    now: LocalDateTime = LocalDateTime.now()
): LocalDateTime {
    val iv = maxOf(1, interval)
    return when (freq) {
        RecurringFrequency.DAILY -> now.toLocalDate().plusDays(iv.toLong()).atStartOfDay()

        RecurringFrequency.WEEKLY -> {
            val target = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
            var d = now.toLocalDate()
            var guard = 0
            while (d.dayOfWeek != target && guard <= 7) {
                d = d.plusDays(1)
                guard++
            }
            // interval=N 表示每 N 周一次：从命中的那周再推进 (N-1) 周
            d.plusWeeks((iv - 1).toLong()).atStartOfDay()
        }

        RecurringFrequency.MONTHLY -> {
            val ym = YearMonth.from(now)
            // 月末天数保护：29~31 号在非满月月份钳制为该月最后一天，不再硬截断到 28
            val dom = min(dayOfMonth.coerceIn(1, 31), ym.lengthOfMonth())
            val candidate = ym.atDay(dom).atStartOfDay()
            if (candidate.isBefore(now)) candidate.plusMonths(iv.toLong()) else candidate
        }

        RecurringFrequency.YEARLY -> {
            val y = now.year
            val m = monthOfYear.coerceIn(1, 12)
            // 月末天数保护：避免「2 月 31 日」等非法日期导致 LocalDate.of 抛异常
            val dom = min(dayOfMonth.coerceIn(1, 31), YearMonth.of(y, m).lengthOfMonth())
            val candidate = LocalDate.of(y, m, dom).atStartOfDay()
            if (candidate.isBefore(now)) candidate.plusYears(iv.toLong()) else candidate
        }
    }
}
