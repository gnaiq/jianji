package com.example.jianji.data.local.repository

import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {
    suspend fun getForMonth(year: Int, month: Int): List<Budget> = dao.getForMonth(year, month)
    suspend fun getForYear(year: Int): List<Budget> = dao.getForYear(year)
    suspend fun getCategoryBudget(categoryId: Long, year: Int, month: Int): Budget? =
        dao.getCategoryBudget(categoryId, year, month)

    suspend fun getTotalBudget(year: Int, month: Int): Budget? = dao.getTotalBudget(year, month)
    fun observeTotalBudget(year: Int, month: Int): Flow<Budget?> = dao.observeTotalBudget(year, month)
    // 预算设置：upsert 语义（原子化，由 DAO @Transaction 保证）
    suspend fun setBudget(budget: Budget) = dao.upsertBudget(budget)
    suspend fun insert(budget: Budget) = dao.insert(budget)
    suspend fun update(budget: Budget) = dao.update(budget)
    suspend fun delete(budget: Budget) = dao.delete(budget)
    suspend fun deleteAll() = dao.deleteAll()
}
