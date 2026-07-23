package com.example.jianji.data

import androidx.room.*

@Dao
interface QuickTemplateDao {
    @Query("SELECT * FROM quick_templates ORDER BY sortOrder ASC, useCount DESC")
    suspend fun getAll(): List<QuickTemplate>

    @Query("SELECT * FROM quick_templates WHERE type = :type ORDER BY sortOrder ASC, useCount DESC")
    suspend fun getByType(type: TransactionType): List<QuickTemplate>

    @Query("SELECT * FROM quick_templates WHERE id = :id")
    suspend fun getById(id: Long): QuickTemplate?

    @Query("UPDATE quick_templates SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Long)

    @Insert
    suspend fun insert(template: QuickTemplate): Long

    @Update
    suspend fun update(template: QuickTemplate)

    @Delete
    suspend fun delete(template: QuickTemplate)

    @Query("DELETE FROM quick_templates")
    suspend fun deleteAll()
}
