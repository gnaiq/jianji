package com.example.jianji.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
com.example.jianji.data.local.entity.*

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<Tag>)

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    // 备份恢复用：整表清空（外键 CASCADE 会连带清理 transaction_tags）
    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    // 某交易关联的标签 id 列表
    @Query("SELECT tagId FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun getTagIdsForTransaction(transactionId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(cross: TransactionTagCrossRef)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    suspend fun clearCrossRefs(transactionId: Long)

    // 删除标签时清理其关联的 crossRef，避免孤儿记录
    @Query("DELETE FROM transaction_tags WHERE tagId = :tagId")
    suspend fun deleteCrossRefsByTag(tagId: Long)

    // 全量 crossRef，用于构建 交易→标签 映射（卡片展示标签）
    @Query("SELECT transactionId, tagId FROM transaction_tags")
    fun observeAllCrossRefs(): Flow<List<TransactionTagCrossRef>>

    // 备份导出用：crossRef 快照
    @Query("SELECT transactionId, tagId FROM transaction_tags")
    suspend fun getAllCrossRefs(): List<TransactionTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<TransactionTagCrossRef>)
}
