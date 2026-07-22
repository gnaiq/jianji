package com.example.jianji.data

import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val categoryDao: CategoryDao) {
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    fun getCategoriesByType(type: CategoryType): Flow<List<Category>> =
        categoryDao.getCategoriesByType(type)

    suspend fun insertCategory(category: Category): Long =
        categoryDao.insert(category)

    suspend fun updateCategory(category: Category) =
        categoryDao.update(category)

    suspend fun deleteCategory(category: Category) =
        categoryDao.delete(category)

    suspend fun getDefaultCategories(): List<Category> =
        categoryDao.getDefaultCategories()

    suspend fun deleteAll() = categoryDao.deleteAll()

    suspend fun getCount(): Int = categoryDao.getCount()
}
