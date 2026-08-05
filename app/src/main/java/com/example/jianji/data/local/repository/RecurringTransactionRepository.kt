package com.example.jianji.data.local.repository

import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import com.example.jianji.data.local.dao.*
import com.example.jianji.data.local.entity.*

class RecurringTransactionRepository(private val dao: RecurringTransactionDao) {
    fun observeAll(): Flow<List<RecurringTransaction>> = dao.observeAll()
    suspend fun getActive(): List<RecurringTransaction> = dao.getActive()
    suspend fun getAll(): List<RecurringTransaction> = dao.getAll()
    suspend fun getById(id: Long): RecurringTransaction? = dao.getById(id)
    suspend fun getDue(now: LocalDateTime): List<RecurringTransaction> = dao.getDue(now)
    suspend fun insert(tx: RecurringTransaction): Long = dao.insert(tx)
    suspend fun update(tx: RecurringTransaction) = dao.update(tx)
    suspend fun delete(tx: RecurringTransaction) = dao.delete(tx)
    suspend fun deleteAll() = dao.deleteAll()
}
