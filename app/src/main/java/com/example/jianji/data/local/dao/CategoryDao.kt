package com.example.jianji.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
com.example.jianji.data.local.entity.*

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>): List<Long>

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE name = :name AND isSystem = 1 LIMIT 1")
    suspend fun getBySystemName(name: String): Category?

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC, name ASC")
    fun getCategoriesByType(type: CategoryType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type AND parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    suspend fun getSiblings(type: CategoryType, parentId: Long): List<Category>

    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDefault = 1")
    suspend fun getDefaultCategories(): List<Category>

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int

    // 修复 B1-4/B5-6：删除分类时把其下全部交易（含回收站软删记录）改挂目标分类，
    // 避免子记录外键悬空导致删分类被 FK 约束拒绝。回收站记录按 deleted_at 过滤展示，
    // categoryId 改挂不影响其"仍在回收站"的语义（B5-6 验收只看记录数）。
    @Query("UPDATE transactions SET categoryId = :targetId WHERE categoryId = :sourceId")
    suspend fun reassignCategory(sourceId: Long, targetId: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun getMaxSortOrder(): Int

    // B7-5：查询某父分类下的直接子分类
    @Query("SELECT * FROM categories WHERE parentId = :parentId")
    suspend fun getChildren(parentId: Long): List<Category>

    // B7-5：删除父分类时，将其子分类升为一级（parentId 置 0），保留数据不丢
    @Query("UPDATE categories SET parentId = 0 WHERE parentId = :parentId")
    suspend fun promoteChildrenToRoot(parentId: Long)
}