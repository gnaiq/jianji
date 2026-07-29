package com.example.jianji.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Insert
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE deleted_at IS NULL AND date >= :startDate AND date < :endDate
        ORDER BY date DESC
    """)
    fun getTransactionsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE deleted_at IS NULL AND categoryId = :categoryId
        ORDER BY date DESC
    """)
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE deleted_at IS NULL AND type = :type
        ORDER BY date DESC
    """)
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    // Filter by account（历史搜索「账户」筛选复用；其余未用的搜索查询已按路线图清理）
    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL AND accountId = :accountId ORDER BY date DESC")
    fun getByAccount(accountId: Long): Flow<List<Transaction>>

    // §8 金额已存为 Long 分；SUM 除以 100 仍以 Double 返回，保持上层 API 不变、降低爆炸半径
    @Query("SELECT SUM(amount_cents)/100.0 FROM transactions WHERE deleted_at IS NULL AND type = :type AND date >= :startDate AND date < :endDate")
    suspend fun getSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double?

    // §1 P0 查询下推：当月收支/今日支出改由 SQL 聚合（吃 date 索引），
    // 替代 ViewModel 中每次发射对全表 List 的三遍内存扫描
    @Query("SELECT SUM(amount_cents)/100.0 FROM transactions WHERE deleted_at IS NULL AND type = :type AND date >= :startDate AND date < :endDate")
    fun observeSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Flow<Double?>

    @Query("SELECT SUM(amount_cents)/100.0 FROM transactions WHERE deleted_at IS NULL AND categoryId = :categoryId AND type = :type AND date >= :startDate AND date < :endDate")
    suspend fun getSumByCategoryAndType(categoryId: Long, type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double?

    // 回收站（§5）：仅已软删的交易，按删除时间倒序
    @Query("SELECT * FROM transactions WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getDeletedTransactions(): Flow<List<Transaction>>

    // 按标签集合筛选（任一命中即返回），排除回收站
    @Query("""
        SELECT * FROM transactions t
        WHERE deleted_at IS NULL AND t.id IN (
            SELECT transactionId FROM transaction_tags WHERE tagId IN (:tagIds)
        )
        ORDER BY t.date DESC
    """)
    fun getTransactionsByTagIds(tagIds: List<Long>): Flow<List<Transaction>>

    @Query("DELETE FROM transactions WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeleted()

    @Query("UPDATE transactions SET deleted_at = NULL WHERE id = :id")
    suspend fun restoreTransaction(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL")
    suspend fun getCount(): Int

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL AND date >= :startDate AND date < :endDate ORDER BY date DESC")
    suspend fun getByDateRangeSnapshot(startDate: LocalDateTime, endDate: LocalDateTime): List<Transaction>

    @Query("SELECT * FROM transactions WHERE deleted_at IS NULL ORDER BY date DESC")
    suspend fun getAllSnapshot(): List<Transaction>

    // 删除账户前把其交易解绑（accountId 置 NULL），避免悬空外键引用
    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :accountId")
    suspend fun clearAccount(accountId: Long)

    // 删除账户前同时清空转账目标账户引用（P1-4 账户删除治理）
    @Query("UPDATE transactions SET toAccountId = NULL WHERE toAccountId = :accountId")
    suspend fun clearToAccount(accountId: Long)
}
