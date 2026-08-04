package com.example.jianji.utils

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jianji.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * 验收 B1-4 / B5-6：删除分类不得级联物理删除交易（含转账引用系统分类、含回收站软删记录）。
 * 删除后该分类下「可见」交易应改挂「未分类」兜底分类，回收站记录保留。
 */
@RunWith(AndroidJUnit4::class)
class CategoryDeleteSafetyTest {

    private lateinit var db: JianjiDatabase
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var txRepo: TransactionRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JianjiDatabase::class.java
        ).allowMainThreadQueries().build()
        categoryRepo = CategoryRepository(db.categoryDao())
        txRepo = TransactionRepository(db.transactionDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `删除普通分类后其交易不丢失 改挂未分类`() = runBlocking {
        val cat = Category(name = "餐饮", type = CategoryType.EXPENSE, sortOrder = 0)
        val catId = categoryRepo.insertCategory(cat)
        txRepo.insertTransaction(
            Transaction(categoryId = catId, amountCents = 1234, type = TransactionType.EXPENSE,
                date = LocalDateTime.now())
        )
        assertEquals(1, txRepo.getAllSnapshot().size)

        categoryRepo.deleteCategory(cat.copy(id = catId))

        // 交易总数不变（不应被 CASCADE 删掉）
        val remaining = txRepo.getAllSnapshot()
        assertEquals("删除分类不应物理删除交易", 1, remaining.size)
        // 且交易改挂到了「未分类」兜底分类
        val uncategorized = categoryRepo.getBySystemName(CategoryRepository.UNCLASSIFIED_NAME)
        assertTrue("应生成未分类兜底分类", uncategorized != null)
        assertEquals(uncategorized!!.id, remaining.first().categoryId)
    }

    @Test
    fun `删除分类不影响回收站软删交易`() = runBlocking {
        val cat = Category(name = "购物", type = CategoryType.EXPENSE, sortOrder = 0)
        val catId = categoryRepo.insertCategory(cat)
        val t = Transaction(categoryId = catId, amountCents = 500, type = TransactionType.EXPENSE,
            date = LocalDateTime.now())
        val tid = txRepo.insertTransaction(t)
        txRepo.softDelete(t.copy(id = tid)) // 进回收站

        categoryRepo.deleteCategory(cat.copy(id = catId))

        // 回收站记录仍在
        val deleted = txRepo.getDeletedTransactions().first()
        assertEquals(1, deleted.size)
        assertEquals(tid, deleted.first().id)
    }

    @Test
    fun `系统分类不可删除 转账交易保留`() = runBlocking {
        // 种植系统「转账」分类（与真实建库一致）
        val transferCatId = categoryRepo.insertCategory(
            Category(name = "转账", type = CategoryType.EXPENSE, isSystem = true, sortOrder = 999)
        )
        val accA = db.accountDao().insert(Account(name = "A"))
        val accB = db.accountDao().insert(Account(name = "B"))
        txRepo.insertTransaction(
            Transaction(categoryId = transferCatId, amountCents = 9999, type = TransactionType.TRANSFER,
                date = LocalDateTime.now(), accountId = accA, toAccountId = accB)
        )
        assertEquals(1, txRepo.getAllSnapshot().size)

        // 尝试删除系统分类（repository 应直接 return，不删）
        categoryRepo.deleteCategory(
            Category(id = transferCatId, name = "转账", type = CategoryType.EXPENSE, isSystem = true, sortOrder = 999)
        )

        assertEquals("系统分类下的转账交易不得被删", 1, txRepo.getAllSnapshot().size)
    }
}
