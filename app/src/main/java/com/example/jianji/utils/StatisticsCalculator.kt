package com.example.jianji.utils

import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import java.time.LocalDateTime
import java.time.YearMonth

data class CategoryStatistics(
    val category: Category,
    val totalAmount: Double,
    val transactionCount: Int,
    val percentage: Double
)

data class PeriodStatistics(
    val periodName: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netAmount: Double,
    val incomeByCategory: List<CategoryStatistics>,
    val expenseByCategory: List<CategoryStatistics>
)

class StatisticsCalculator {

    /**
     * 计算周统计
     */
    fun calculateWeeklyStatistics(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        startDate: LocalDateTime
    ): PeriodStatistics {
        val endDate = startDate.plusDays(7)
        val weekTransactions = transactions.filter {
            it.date >= startDate && it.date < endDate
        }
        return calculatePeriodStatistics(weekTransactions, categories, "本周")
    }

    /**
     * 计算月统计
     */
    fun calculateMonthlyStatistics(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        yearMonth: YearMonth = YearMonth.now()
    ): PeriodStatistics {
        val startDate = yearMonth.atDay(1).atStartOfDay()
        val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)
        val monthTransactions = transactions.filter {
            it.date >= startDate && it.date <= endDate
        }
        return calculatePeriodStatistics(monthTransactions, categories, yearMonth.toString())
    }

    /**
     * 计算年统计
     */
    fun calculateYearlyStatistics(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        year: Int = java.time.Year.now().value
    ): PeriodStatistics {
        val startDate = LocalDateTime.of(year, 1, 1, 0, 0, 0)
        val endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59)
        val yearTransactions = transactions.filter {
            it.date >= startDate && it.date <= endDate
        }
        return calculatePeriodStatistics(yearTransactions, categories, "$year 年")
    }

    /**
     * 按分类统计
     */
    fun calculateByCategory(
        transactions: List<Transaction>,
        categories: Map<Long, Category>
    ): Map<Category, Pair<Double, Int>> {
        return transactions.groupBy { it.categoryId }
            .mapNotNull { (categoryId, txns) ->
                val category = categories[categoryId] ?: return@mapNotNull null
                category to (txns.sumOf { it.amount } to txns.size)
            }
            .toMap()
    }

    /**
     * 获取月度趋势数据
     */
    fun getMonthlyTrend(
        transactions: List<Transaction>,
        months: Int = 12
    ): List<Pair<String, Double>> {
        val now = YearMonth.now()
        return (0 until months).map { i ->
            val yearMonth = now.minusMonths(i.toLong())
            val startDate = yearMonth.atDay(1).atStartOfDay()
            val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)
            val monthTotal = transactions
                .filter { it.date >= startDate && it.date <= endDate && it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            yearMonth.toString() to monthTotal
        }.reversed()
    }

    private fun calculatePeriodStatistics(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        periodName: String
    ): PeriodStatistics {
        val incomeTransactions = transactions.filter { it.type == TransactionType.INCOME }
        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }

        val totalIncome = incomeTransactions.sumOf { it.amount }
        val totalExpense = expenseTransactions.sumOf { it.amount }

        val incomeByCategory = calculateCategoryStatistics(incomeTransactions, categories, totalIncome)
        val expenseByCategory = calculateCategoryStatistics(expenseTransactions, categories, totalExpense)

        return PeriodStatistics(
            periodName = periodName,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netAmount = totalIncome - totalExpense,
            incomeByCategory = incomeByCategory,
            expenseByCategory = expenseByCategory
        )
    }

    private fun calculateCategoryStatistics(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        total: Double
    ): List<CategoryStatistics> {
        if (total == 0.0) return emptyList()

        return transactions.groupBy { it.categoryId }
            .mapNotNull { (categoryId, txns) ->
                val category = categories[categoryId] ?: return@mapNotNull null
                val amount = txns.sumOf { it.amount }
                CategoryStatistics(
                    category = category,
                    totalAmount = amount,
                    transactionCount = txns.size,
                    percentage = (amount / total) * 100
                )
            }
            .sortByDescending { it.totalAmount }
    }
}
