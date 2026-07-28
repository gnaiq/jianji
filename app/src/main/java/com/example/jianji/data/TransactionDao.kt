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

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE date >= :startDate AND date < :endDate 
        ORDER BY date DESC
    """)
    fun getTransactionsByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE categoryId = :categoryId 
        ORDER BY date DESC
    """)
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE type = :type 
        ORDER BY date DESC
    """)
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    // Search by description text
    @Query("""
        SELECT * FROM transactions 
        WHERE description LIKE '%' || :query || '%' 
        ORDER BY date DESC
    """)
    fun searchByDescription(query: String): Flow<List<Transaction>>

    // Search by amount range
    @Query("""
        SELECT * FROM transactions 
        WHERE amount >= :minAmount AND amount <= :maxAmount 
        ORDER BY date DESC
    """)
    fun searchByAmountRange(minAmount: Double, maxAmount: Double): Flow<List<Transaction>>

    // Combined search
    @Query("""
        SELECT * FROM transactions 
        WHERE description LIKE '%' || :query || '%' 
        AND amount >= :minAmount AND amount <= :maxAmount
        ORDER BY date DESC
    """)
    fun searchByDescriptionAndAmount(query: String, minAmount: Double, maxAmount: Double): Flow<List<Transaction>>

    // Filter by account
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun getByAccount(accountId: Long): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date >= :startDate AND date < :endDate")
    suspend fun getSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double?

    // §1 P0 查询下推：当月收支/今日支出改由 SQL 聚合（吃 date 索引），
    // 替代 ViewModel 中每次发射对全表 List 的三遍内存扫描
    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND date >= :startDate AND date < :endDate")
    fun observeSumByType(type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE categoryId = :categoryId AND type = :type AND date >= :startDate AND date < :endDate")
    suspend fun getSumByCategoryAndType(categoryId: Long, type: TransactionType, startDate: LocalDateTime, endDate: LocalDateTime): Double?

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date < :endDate ORDER BY date DESC")
    suspend fun getByDateRangeSnapshot(startDate: LocalDateTime, endDate: LocalDateTime): List<Transaction>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllSnapshot(): List<Transaction>

    // 删除账户前把其交易解绑（accountId 置 NULL），避免悬空外键引用
    @Query("UPDATE transactions SET accountId = NULL WHERE accountId = :accountId")
    suspend fun clearAccount(accountId: Long)
}
