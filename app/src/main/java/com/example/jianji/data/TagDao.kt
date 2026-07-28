package com.example.jianji.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

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

    // 按标签筛选交易（回收站之外的有效交易）
    @Transaction
    @Query(
        """SELECT t.* FROM transactions t
           INNER JOIN transaction_tags tt ON tt.transactionId = t.id
           WHERE tt.tagId = :tagId AND t.deleted_at IS NULL
           ORDER BY t.date DESC"""
    )
    fun getTransactionsByTag(tagId: Long): Flow<List<Transaction>>

    // 按多个标签筛选（IN 子句，OR 语义）
    @Transaction
    @Query(
        """SELECT t.* FROM transactions t
           INNER JOIN transaction_tags tt ON tt.transactionId = t.id
           WHERE tt.tagId IN (:tagIds) AND t.deleted_at IS NULL
           ORDER BY t.date DESC"""
    )
    fun getTransactionsByTagIds(tagIds: List<Long>): Flow<List<Transaction>>

    // 全量 crossRef，用于构建 交易→标签 映射（卡片展示标签）
    @Query("SELECT transactionId, tagId FROM transaction_tags")
    fun observeAllCrossRefs(): Flow<List<TransactionTagCrossRef>>
}
