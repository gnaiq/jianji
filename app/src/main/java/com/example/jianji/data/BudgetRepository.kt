package com.example.jianji.data

class BudgetRepository(private val dao: BudgetDao) {
    suspend fun getForMonth(year: Int, month: Int): List<Budget> = dao.getForMonth(year, month)
    suspend fun getForYear(year: Int): List<Budget> = dao.getForYear(year)
    suspend fun getCategoryBudget(categoryId: Long, year: Int, month: Int): Budget? =
        dao.getCategoryBudget(categoryId, year, month)

    suspend fun getTotalBudget(year: Int, month: Int): Budget? = dao.getTotalBudget(year, month)
    suspend fun setBudget(budget: Budget) = dao.insert(budget)
    suspend fun update(budget: Budget) = dao.update(budget)
    suspend fun delete(budget: Budget) = dao.delete(budget)
    suspend fun deleteAll() = dao.deleteAll()
}
