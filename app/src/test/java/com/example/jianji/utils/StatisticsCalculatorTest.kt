package com.example.jianji.utils

import com.example.jianji.data.Category
import com.example.jianji.data.CategoryType
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class StatisticsCalculatorTest {
    private fun cat(id: Long, name: String) = Category(id = id, name = name, type = CategoryType.EXPENSE)
    private fun tx(id: Long, catId: Long, amount: Double, type: TransactionType, date: LocalDateTime = LocalDateTime.now()) =
        Transaction(id = id, categoryId = catId, amountCents = (amount * 100).toLong(), type = type, description = "", date = date)

    @Test fun calculateByCategoryGroupsAndSums() {
        val c1 = cat(1, "餐饮")
        val c2 = cat(2, "交通")
        val txs = listOf(
            tx(1, 1, 10.0, TransactionType.EXPENSE),
            tx(2, 1, 5.0, TransactionType.EXPENSE),
            tx(3, 2, 20.0, TransactionType.EXPENSE),
            tx(4, 1, 100.0, TransactionType.EXPENSE)
        )
        val map = mapOf(1L to c1, 2L to c2)
        val result = StatisticsCalculator().calculateByCategory(txs, map)
        assertEquals(2, result.size)
        assertEquals(115.0, result[c1]!!.first, 0.0001)   // 10 + 5 + 100
        assertEquals(3, result[c1]!!.second)              // 笔数
        assertEquals(20.0, result[c2]!!.first, 0.0001)
        assertEquals(1, result[c2]!!.second)
    }

    @Test fun calculateWeeklyStatisticsFiltersRange() {
        val start = LocalDateTime.of(2026, 4, 13, 0, 0)
        val inRange = start.plusDays(2) // 2026-04-15，落在 [start, start+7) 内
        val txs = listOf(tx(1, 1, 10.0, TransactionType.EXPENSE, inRange))
        val map = mapOf(1L to cat(1, "餐饮"))
        val r = StatisticsCalculator().calculateWeeklyStatistics(txs, map, start)
        assertEquals(10.0, r.totalExpense, 0.0001)
    }
}
