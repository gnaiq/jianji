package com.example.jianji.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateUtils {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA)

    fun formatDate(date: LocalDateTime): String {
        return date.format(dateFormatter)
    }

    fun formatDateTime(date: LocalDateTime): String {
        return date.format(dateTimeFormatter)
    }

    fun formatMonth(yearMonth: YearMonth): String {
        return yearMonth.format(monthFormatter)
    }

    fun parseDate(dateString: String): LocalDateTime {
        return LocalDate.parse(dateString, dateFormatter).atStartOfDay()
    }

    fun getWeekStart(date: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val dayOfWeek = date.dayOfWeek.value
        return date.minusDays((dayOfWeek - 1).toLong()).toLocalDate().atStartOfDay()
    }

    fun getWeekEnd(date: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val dayOfWeek = date.dayOfWeek.value
        return date.plusDays((7 - dayOfWeek).toLong()).toLocalDate().atTime(23, 59, 59)
    }

    fun getMonthStart(yearMonth: YearMonth = YearMonth.now()): LocalDateTime {
        return yearMonth.atDay(1).atStartOfDay()
    }

    fun getMonthEnd(yearMonth: YearMonth = YearMonth.now()): LocalDateTime {
        return yearMonth.atEndOfMonth().atTime(23, 59, 59)
    }

    fun getYearStart(year: Int = java.time.Year.now().value): LocalDateTime {
        return LocalDateTime.of(year, 1, 1, 0, 0, 0)
    }

    fun getYearEnd(year: Int = java.time.Year.now().value): LocalDateTime {
        return LocalDateTime.of(year, 12, 31, 23, 59, 59)
    }

    fun isToday(date: LocalDateTime): Boolean {
        return date.toLocalDate() == LocalDate.now()
    }

    fun isYesterday(date: LocalDateTime): Boolean {
        return date.toLocalDate() == LocalDate.now().minusDays(1)
    }

    fun isThisMonth(date: LocalDateTime): Boolean {
        val now = LocalDateTime.now()
        return date.year == now.year && date.monthValue == now.monthValue
    }

    fun isThisYear(date: LocalDateTime): Boolean {
        return date.year == LocalDateTime.now().year
    }

    fun getRelativeTimeString(date: LocalDateTime): String {
        val now = LocalDateTime.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(date.toLocalDate(), now.toLocalDate())

        return when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days in 2..6 -> "${days}天前"
            days in 7..29 -> "${days / 7}周前"
            days >= 30 -> "${days / 30}个月前"
            else -> formatDate(date)
        }
    }
}
