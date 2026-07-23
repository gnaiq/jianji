package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CategoryRepository(private val dao: CategoryDao) {
    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    fun getCategoriesByType(type: TransactionType): Flow<List<Category>> {
        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        return dao.getCategoriesByType(ct)
    }

    suspend fun insertCategory(category: Category): Long = dao.insert(category)
    suspend fun updateCategory(category: Category) = dao.update(category)
    suspend fun deleteCategory(category: Category) = dao.delete(category)
    suspend fun getDefaultCategories(): List<Category> = dao.getDefaultCategories()
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getCount(): Int = dao.getCount()
    suspend fun getMaxSortOrder(): Int = dao.getMaxSortOrder()

    suspend fun seedDefaults() {
        if (dao.getCount() == 0) {
            for (cat in createDefaultCategories()) {
                dao.insert(cat)
            }
        }
    }
}
