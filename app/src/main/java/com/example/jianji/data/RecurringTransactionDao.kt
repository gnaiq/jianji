package com.example.jianji.data

import androidx.room.*

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY nextRunDate ASC")
    suspend fun getActive(): List<RecurringTransaction>

    @Query("SELECT * FROM recurring_transactions ORDER BY nextRunDate ASC")
    suspend fun getAll(): List<RecurringTransaction>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransaction?

    @Query("SELECT * FROM recurring_transactions WHERE nextRunDate <= :now AND isActive = 1")
    suspend fun getDue(now: java.time.LocalDateTime): List<RecurringTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: RecurringTransaction): Long

    @Update
    suspend fun update(tx: RecurringTransaction)

    @Delete
    suspend fun delete(tx: RecurringTransaction)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}
