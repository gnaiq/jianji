package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.YearMonth
import com.example.jianji.data.*
import com.example.jianji.data.*

class TransactionRepository(private val transactionDao: TransactionDao) {
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId)

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getTransactionsByType(type)

    fun getByAccount(accountId: Long): Flow<List<Transaction>> =
        transactionDao.getByAccount(accountId)

    suspend fun insertTransaction(transaction: Transaction): Long {
        // B1-2 数据层兜底：转账交易必须有转出/转入账户且不可相同，
        // 防止绕过 UI 校验（导入/API）写入悬空转账导致对账缺口。
        if (transaction.type == TransactionType.TRANSFER) {
            require(transaction.accountId != null) { "TRANSFER 必须指定转出账户 accountId" }
            require(transaction.toAccountId != null) { "TRANSFER 必须指定转入账户 toAccountId" }
            require(transaction.accountId != transaction.toAccountId) { "TRANSFER 转出与转入账户不可相同" }
        }
        return transactionDao.insert(transaction)
    }

    suspend fun insertAll(transactions: List<Transaction>): List<Long> =
        transactionDao.insertAll(transactions)

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.delete(transaction)

    // 软删（回收站，§5）：标记 deleted_at，不物理删除
    suspend fun softDelete(transaction: Transaction) =
        transactionDao.update(transaction.copy(deletedAt = LocalDateTime.now()))

    fun getDeletedTransactions(): Flow<List<Transaction>> =
        transactionDao.getDeletedTransactions()

    suspend fun restoreTransaction(id: Long) = transactionDao.restoreTransaction(id)

    suspend fun purgeDeleted() = transactionDao.purgeDeleted()

    fun getTransactionsByTagIds(tagIds: List<Long>): Flow<List<Transaction>> =
        transactionDao.getTransactionsByTagIds(tagIds)

    suspend fun getSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double =
        transactionDao.getSumByType(type, startDate, endDate) ?: 0.0

    fun observeSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Flow<Double?> =
        transactionDao.observeSumByType(type, startDate, endDate)

    // P3-1 余额 SQL 聚合（UNION ALL 四类型，吃 accountId 索引）
    fun observeAccountBalances(): Flow<List<AccountBalance>> =
        transactionDao.observeAccountBalances()

    suspend fun getSumByCategoryAndType(categoryId: Long, type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double =
        transactionDao.getSumByCategoryAndType(categoryId, type, startDate, endDate) ?: 0.0

    suspend fun deleteAll() = transactionDao.deleteAll()

    // 删除账户前，将该账户下的交易 accountId 置空（解绑），避免悬空引用
    suspend fun reassignAccountToNull(accountId: Long) {
        transactionDao.clearAccount(accountId)
        transactionDao.clearToAccount(accountId)
    }

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
        val budgetCents = budget.amountCents / 100.0
        return BudgetProgress(spent, budgetCents, if (budgetCents > 0) spent / budgetCents else 0.0)
    }
}

data class BudgetProgress(
    val spent: Double,
    val budget: Double,
    val ratio: Double // 0.0 ~ 1.0+, 超过 1.0 表示超支
)
