package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime

/**
 * 验收 U-1 / B1-2：转账交易必须有转出与转入账户（且不相同）。
 * 数据层 TransactionRepository.insertTransaction 提供兜底 require，
 * 防止绕过 UI 校验（导入/API）写入悬空转账导致对账缺口。
 */
class TransferValidationTest {
    private class FakeDao : TransactionDao {
        var lastInserted: Transaction? = null
        override suspend fun insert(transaction: Transaction): Long {
            lastInserted = transaction
            return transaction.id
        }
        // 其余为测试不需要的桩
        override fun getAllTransactions(): Flow<List<Transaction>> = emptyFlow()
        override fun getTransactionsByDateRange(start: LocalDateTime, end: LocalDateTime): Flow<List<Transaction>> = emptyFlow()
        override fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> = emptyFlow()
        override fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> = emptyFlow()
        override fun getByAccount(accountId: Long): Flow<List<Transaction>> = emptyFlow()
        override suspend fun insertAll(list: List<Transaction>): List<Long> = emptyList()
        override suspend fun update(transaction: Transaction) {}
        override suspend fun delete(transaction: Transaction) {}
        override fun getDeletedTransactions(): Flow<List<Transaction>> = emptyFlow()
        override suspend fun getById(id: Long): Transaction? = null
        override suspend fun restoreTransaction(id: Long) {}
        override suspend fun purgeDeleted() {}
        override fun getTransactionsByTagIds(ids: List<Long>): Flow<List<Transaction>> = emptyFlow()
        override suspend fun getAllSnapshot(): List<Transaction> = emptyList()
        override suspend fun getAllIncludingDeletedSnapshot(): List<Transaction> = emptyList()
        override fun getByDateRangeSnapshot(startDate: LocalDateTime, endDate: LocalDateTime): List<Transaction> = emptyList()
        override suspend fun getSumByType(type: TransactionType, start: LocalDateTime, end: LocalDateTime): Double? = 0.0
        override fun observeSumByType(type: TransactionType, start: LocalDateTime, end: LocalDateTime): Flow<Double?> = emptyFlow()
        override suspend fun getSumByCategoryAndType(categoryId: Long, type: TransactionType, start: LocalDateTime, end: LocalDateTime): Double? = 0.0
        override suspend fun deleteAll() {}
        override suspend fun getCount(): Int = 0
        override suspend fun clearAccount(accountId: Long) {}
        override suspend fun clearToAccount(accountId: Long) {}
    }

    private fun repo() = TransactionRepository(FakeDao())

    private fun transfer(accountId: Long?, toAccountId: Long?) = Transaction(
        id = 1, categoryId = 1, amountCents = 100, type = TransactionType.TRANSFER,
        date = LocalDateTime.now(), accountId = accountId, toAccountId = toAccountId
    )

    @Test
    fun `TRANSFER 缺转出账户被拒`() {
        try {
            runBlocking { repo().insertTransaction(transfer(null, 2L)) }
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `TRANSFER 缺转入账户被拒`() {
        try {
            runBlocking { repo().insertTransaction(transfer(1L, null)) }
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `TRANSFER 转出与转入相同被拒`() {
        try {
            runBlocking { repo().insertTransaction(transfer(1L, 1L)) }
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `合法 TRANSFER 放行`() {
        val dao = FakeDao()
        runBlocking { TransactionRepository(dao).insertTransaction(transfer(1L, 2L)) }
        assertEquals(1L, dao.lastInserted?.id)
    }
}
