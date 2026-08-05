package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import com.example.jianji.data.*
import com.example.jianji.data.*

class TagRepository(private val tagDao: TagDao) {
    fun observeAll(): Flow<List<Tag>> = tagDao.observeAll()

    suspend fun getAll(): List<Tag> = tagDao.getAll()

    suspend fun insert(tag: Tag): Long = tagDao.insert(tag)

    suspend fun update(tag: Tag) = tagDao.update(tag)

    suspend fun deleteAll() = tagDao.deleteAll()

    suspend fun delete(tag: Tag) {
        tagDao.deleteCrossRefsByTag(tag.id)
        tagDao.delete(tag)
    }

    suspend fun getTagIdsForTransaction(transactionId: Long): List<Long> =
        tagDao.getTagIdsForTransaction(transactionId)

    fun observeAllCrossRefs(): Flow<List<TransactionTagCrossRef>> = tagDao.observeAllCrossRefs()

    // 重新绑定某交易的标签集合（先清后插）
    suspend fun setTransactionTags(transactionId: Long, tagIds: List<Long>) {
        tagDao.clearCrossRefs(transactionId)
        tagIds.forEach { tagDao.insertCrossRef(TransactionTagCrossRef(transactionId, it)) }
    }
}
