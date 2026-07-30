package com.example.jianji.utils

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jianji.data.JianjiDatabase
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导入/恢复路径集成测试（P1-阶段二·项5）。
 * 用 Room in-memory 数据库覆盖 importFromJson 的 4 个关键场景：
 *  ① version=2 全量恢复（6 张表数据齐全）
 *  ② 旧格式（无 version）仅恢复交易+分类，其余表保留
 *  ③ 坏记录逐条跳过且 skippedCount 正确
 *  ④ 金额精度：8.20 元恢复后 amountCents == 820
 */
@RunWith(AndroidJUnit4::class)
class DataImportManagerTest {

    private lateinit var db: JianjiDatabase
    private val manager = DataImportManager()
    private val gson = Gson()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            JianjiDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------- 场景①：version=2 全量恢复，6 张表数据齐全 ----------
    @Test
    fun fullRestore_version2_restoresAllSixTables() = runBlocking {
        val data = ImportData(
            version = 2,
            categories = listOf(
                CategoryImport(id = 1, name = "餐饮", type = "EXPENSE"),
                CategoryImport(id = 2, name = "工资", type = "INCOME")
            ),
            transactions = listOf(
                TransactionImport(
                    id = 1, categoryId = 1, amount = 12.5, type = "EXPENSE",
                    description = "午饭", date = "2026-04-15T12:00:00"
                ),
                TransactionImport(
                    id = 2, categoryId = 2, amount = 8000.0, type = "INCOME",
                    description = "月薪", date = "2026-04-01T09:00:00"
                )
            ),
            accounts = listOf(AccountImport(id = 1, name = "现金", balance = 100.0)),
            budgets = listOf(BudgetImport(id = 1, categoryId = 1, amount = 500.0, period = "MONTHLY", year = 2026, month = 4)),
            recurringTransactions = listOf(
                RecurringImport(
                    id = 1, categoryId = 1, amount = 30.0, type = "EXPENSE",
                    description = "订阅", frequency = "MONTHLY", nextRunDate = "2026-05-01T00:00:00"
                )
            ),
            quickTemplates = listOf(
                TemplateImport(id = 1, categoryId = 1, amount = 15.0, type = "EXPENSE", description = "咖啡")
            )
        )

        val result = manager.importFromJson(gson.toJson(data), db)

        assertTrue("应为全量恢复", result.isFullRestore)
        assertEquals(2, result.transactionCount)
        assertEquals(0, result.skippedCount)
        // 6 张表逐一断言数据齐全
        assertEquals(2, db.transactionDao().getAllSnapshot().size)
        assertEquals(2, db.categoryDao().getAllCategories().first().size)
        assertEquals(1, db.accountDao().getAll().size)
        assertEquals(1, db.budgetDao().getAll().size)
        assertEquals(1, db.recurringTransactionDao().getAll().size)
        assertEquals(1, db.quickTemplateDao().getAll().size)
    }

    // ---------- 场景②：旧格式（无 version）仅恢复交易+分类，账户等表保留 ----------
    @Test
    fun legacyRestore_noVersion_keepsOtherTables() = runBlocking {
        // 预置一条账户/预算/周期/模板，模拟旧格式恢复时应保留的数据
        db.accountDao().insertAll(listOf(com.example.jianji.data.Account(id = 99, name = "保留账户")))
        db.budgetDao().insertAll(
            listOf(
                com.example.jianji.data.Budget(
                    id = 99, categoryId = null, amount = 1000.0,
                    period = com.example.jianji.data.BudgetPeriod.MONTHLY, year = 2026, month = 4
                )
            )
        )

        val legacy = ImportData(
            version = null, // 旧格式
            categories = listOf(CategoryImport(id = 1, name = "餐饮", type = "EXPENSE")),
            transactions = listOf(
                TransactionImport(id = 1, categoryId = 1, amount = 20.0, type = "EXPENSE", date = "2026-04-15T12:00:00")
            )
        )

        val result = manager.importFromJson(gson.toJson(legacy), db)

        assertTrue("旧格式不应为全量恢复", !result.isFullRestore)
        assertEquals(1, result.transactionCount)
        assertEquals(1, db.transactionDao().getAllSnapshot().size)
        assertEquals(1, db.categoryDao().getAllCategories().first().size)
        // 账户/预算表应保留，未被清空
        assertEquals(1, db.accountDao().getAll().size)
        assertEquals(1, db.budgetDao().getAll().size)
    }

    // ---------- 场景③：坏记录逐条跳过，skippedCount 正确 ----------
    @Test
    fun badRecords_skippedIndividually_withCorrectCount() = runBlocking {
        val data = ImportData(
            version = 2,
            categories = listOf(CategoryImport(id = 1, name = "餐饮", type = "EXPENSE")),
            transactions = listOf(
                // 合法
                TransactionImport(id = 1, categoryId = 1, amount = 10.0, type = "EXPENSE", date = "2026-04-15T12:00:00"),
                // 非法日期 -> 跳过
                TransactionImport(id = 2, categoryId = 1, amount = 20.0, type = "EXPENSE", date = "not-a-date"),
                // 非法类型 -> 跳过
                TransactionImport(id = 3, categoryId = 1, amount = 30.0, type = "WEIRD", date = "2026-04-16T12:00:00")
            ),
            recurringTransactions = listOf(
                // 合法
                RecurringImport(id = 1, categoryId = 1, amount = 30.0, type = "EXPENSE", frequency = "MONTHLY", nextRunDate = "2026-05-01T00:00:00"),
                // 非法周期 -> 跳过
                RecurringImport(id = 2, categoryId = 1, amount = 30.0, type = "EXPENSE", frequency = "BADFREQ", nextRunDate = "2026-05-01T00:00:00"),
                // 非法 nextRunDate -> 跳过
                RecurringImport(id = 3, categoryId = 1, amount = 30.0, type = "EXPENSE", frequency = "WEEKLY", nextRunDate = "bad-date")
            )
        )

        val result = manager.importFromJson(gson.toJson(data), db)

        // 交易：1 合法 + 2 坏；周期：1 合法 + 2 坏 -> 共 4 条跳过
        assertEquals(1, result.transactionCount)
        assertEquals(4, result.skippedCount)
        assertEquals(1, db.transactionDao().getAllSnapshot().size)
        assertEquals(1, db.recurringTransactionDao().getAll().size)
    }

    // ---------- 场景④：金额精度，8.20 元 -> amountCents == 820 ----------
    @Test
    fun amountPrecision_820cents() = runBlocking {
        val data = ImportData(
            version = 2,
            categories = listOf(CategoryImport(id = 1, name = "餐饮", type = "EXPENSE")),
            transactions = listOf(
                TransactionImport(id = 1, categoryId = 1, amount = 8.20, type = "EXPENSE", date = "2026-04-15T12:00:00")
            )
        )

        manager.importFromJson(gson.toJson(data), db)

        val restored = db.transactionDao().getAllSnapshot()
        assertEquals(1, restored.size)
        assertEquals(820L, restored[0].amountCents)
    }
}
