package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.YearMonth

class TransactionRepository(private val transactionDao: TransactionDao) {
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId)

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type)

    fun searchByDescription(query: String): Flow<List<Transaction>> =
        transactionDao.searchByDescription(query)

    fun searchByAmountRange(minAmount: Double, maxAmount: Double): Flow<List<Transaction>> =
        transactionDao.searchByAmountRange(minAmount, maxAmount)

    fun searchByDescriptionAndAmount(query: String, minAmount: Double, maxAmount: Double): Flow<List<Transaction>> =
        transactionDao.searchByDescriptionAndAmount(query, minAmount, maxAmount)

    fun getByAccount(accountId: Long): Flow<List<Transaction>> =
        transactionDao.getByAccount(accountId)

    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insert(transaction)

    suspend fun insertAll(transactions: List<Transaction>): List<Long> =
        transactionDao.insertAll(transactions)

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.delete(transaction)

    suspend fun getSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double =
        transactionDao.getSumByType(type, startDate, endDate) ?: 0.0

    suspend fun getSumByCategoryAndType(categoryId: Long, type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double =
        transactionDao.getSumByCategoryAndType(categoryId, type, startDate, endDate) ?: 0.0

    suspend fun deleteAll() = transactionDao.deleteAll()

    suspend fun getCount(): Int = transactionDao.getCount()

    suspend fun getByDateRangeSnapshot(startDate: LocalDateTime, endDate: LocalDateTime): List<Transaction> =
        transactionDao.getByDateRangeSnapshot(startDate, endDate)

    suspend fun getAllSnapshot(): List<Transaction> = transactionDao.getAllSnapshot()

    // 预算进度计算
    suspend fun getBudgetProgress(
        year: Int,
        month: Int,
        budget: Budget?,
        categoryId: Long? = null
    ): BudgetProgress {
        if (budget == null) return BudgetProgress(0.0, 0.0, 0.0)
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay()
        val spent = if (categoryId != null) {
            transactionDao.getSumByCategoryAndType(categoryId, TransactionType.EXPENSE, start, end) ?: 0.0
        } else {
            transactionDao.getSumByType(TransactionType.EXPENSE, start, end) ?: 0.0
        }
        return BudgetProgress(spent, budget.amount, if (budget.amount > 0) spent / budget.amount else 0.0)
    }
}

data class BudgetProgress(
    val spent: Double,
    val budget: Double,
    val ratio: Double // 0.0 ~ 1.0+, 超过 1.0 表示超支
)
