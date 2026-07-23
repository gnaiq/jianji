package com.example.jianji.data

import java.time.LocalDateTime

class RecurringTransactionRepository(private val dao: RecurringTransactionDao) {
    suspend fun getActive(): List<RecurringTransaction> = dao.getActive()
    suspend fun getAll(): List<RecurringTransaction> = dao.getAll()
    suspend fun getById(id: Long): RecurringTransaction? = dao.getById(id)
    suspend fun getDue(now: LocalDateTime): List<RecurringTransaction> = dao.getDue(now)
    suspend fun insert(tx: RecurringTransaction): Long = dao.insert(tx)
    suspend fun update(tx: RecurringTransaction) = dao.update(tx)
    suspend fun delete(tx: RecurringTransaction) = dao.delete(tx)
}
