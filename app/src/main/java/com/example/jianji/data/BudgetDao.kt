package com.example.jianji.data

import androidx.room.*

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE year = :year AND month = :month")
    suspend fun getForMonth(year: Int, month: Int): List<Budget>

    @Query("SELECT * FROM budgets WHERE year = :year AND period = 'YEARLY'")
    suspend fun getForYear(year: Int): List<Budget>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND year = :year AND month = :month LIMIT 1")
    suspend fun getCategoryBudget(categoryId: Long, year: Int, month: Int): Budget?

    @Query("SELECT * FROM budgets WHERE categoryId IS NULL AND year = :year AND month = :month LIMIT 1")
    suspend fun getTotalBudget(year: Int, month: Int): Budget?

    @Query("SELECT * FROM budgets WHERE categoryId IS NULL AND year = :year AND month = :month LIMIT 1")
    fun observeTotalBudget(year: Int, month: Int): Flow<Budget?>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<Budget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Insert
    suspend fun insertAll(budgets: List<Budget>): List<Long>

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
