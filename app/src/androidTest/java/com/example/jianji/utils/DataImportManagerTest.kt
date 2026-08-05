package com.example.jianji.utils

import com.example.jianji.core.backup.DataImportManager
import com.example.jianji.core.backup.ImportData
import com.example.jianji.core.backup.TransactionImport
import com.example.jianji.core.backup.CategoryImport
import com.example.jianji.core.backup.RecurringImport
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.jianji.data.*
import com.example.jianji.ui.viewmodel.SettingsViewModel
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
 *  ⑤ version=3 标签及交易-标签关联往返恢复
 *  ⑥ version=4 系统分类 isSystem 往返
 *  ⑦ 旧备份（v<4）同名「转账」升格为系统分类
 *  ⑧ 回收站（软删）交易在备份中保留
 *  ⑨ 清除数据重置默认分类并清空标签
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
            accounts = listOf(AccountImport(id = 1, name = "现金")),
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
        // 旧备份（v<4）无 isSystem 字段且未含「转账」分类，按「找不到才新建」自动补种系统转账分类
        val restoredCats = db.categoryDao().getAllCategories().first()
        assertEquals(3, restoredCats.size)
        assertTrue("应自动补种系统转账分类", restoredCats.any { it.name == "转账" && it.isSystem })
        assertEquals(1, db.accountDao().getAll().size)
        assertEquals(1, db.budgetDao().getAll().size)
        assertEquals(1, db.recurringTransactionDao().getAll().size)
        assertEquals(1, db.quickTemplateDao().getAll().size)
    }

    // ---------- 场景⑤：version=3 标签 + 交易-标签关联往返恢复 ----------
    @Test
    fun fullRestore_version3_restoresTagsAndCrossRefs() = runBlocking {
        val data = ImportData(
            version = 3,
            categories = listOf(CategoryImport(id = 1, name = "餐饮", type = "EXPENSE")),
            transactions = listOf(
                TransactionImport(
                    id = 1, categoryId = 1, amount = 12.5, type = "EXPENSE",
                    description = "午饭", date = "2026-04-15T12:00:00"
                )
            ),
            tags = listOf(
                TagImport(id = 1, name = "报销", color = "#E57373", icon = "💰"),
                TagImport(id = 2, name = "出差", color = "#64B5F6", icon = "✈️")
            ),
            transactionTags = listOf(
                TransactionTagImport(transactionId = 1, tagId = 1),
                TransactionTagImport(transactionId = 1, tagId = 2),
                // 悬空关联：交易 999 不存在，须被过滤而非触发外键失败
                TransactionTagImport(transactionId = 999, tagId = 1)
            )
        )

        val result = manager.importFromJson(gson.toJson(data), db)

        assertTrue("version=3 应为全量恢复", result.isFullRestore)
        assertEquals(2, db.tagDao().getAll().size)
        val refs = db.tagDao().getAllCrossRefs()
        assertEquals("悬空关联应被过滤", 2, refs.size)
        assertEquals(setOf(1L, 2L), refs.map { it.tagId }.toSet())
    }

    // ---------- 场景②：旧格式（无 version）仅恢复交易+分类，账户等表保留 ----------
    @Test
    fun legacyRestore_noVersion_keepsOtherTables() = runBlocking {
        // 预置一条账户/预算/周期/模板，模拟旧格式恢复时应保留的数据
        db.accountDao().insertAll(listOf(com.example.jianji.data.Account(id = 99, name = "保留账户")))
        db.budgetDao().insertAll(
            listOf(
                com.example.jianji.data.Budget(
                    id = 99, categoryId = null, amountCents = 100000,
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
        // 旧格式恢复同样按「找不到才新建」补种系统转账分类（餐饮 + 转账 = 2）
        val restoredCatsLegacy = db.categoryDao().getAllCategories().first()
        assertEquals(2, restoredCatsLegacy.size)
        assertTrue("应自动补种系统转账分类", restoredCatsLegacy.any { it.name == "转账" && it.isSystem })
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

    // ---------- 场景⑥：version=4 系统分类 isSystem 正确往返（P0-3）----------
    @Test
    fun roundTrip_version4_preservesSystemCategory() = runBlocking {
        db.categoryDao().insertAll(listOf(
            Category(
                id = 1, name = "转账", type = CategoryType.EXPENSE,
                icon = "🔄", color = "#6200EE", isSystem = true, sortOrder = 999
            )
        ))
        val json = manager.generateExportJson(db)
        val exported = gson.fromJson(json, ImportData::class.java)
        assertEquals(4, exported.version)
        assertTrue(exported.categories.any { it.name == "转账" && it.isSystem })

        manager.importFromJson(json, db)
        val restored = db.categoryDao().getAllCategories().first()
        assertTrue("恢复后系统转账分类应保留 isSystem", restored.any { it.name == "转账" && it.isSystem })
    }

    // ---------- 场景⑦：旧备份（v<4）同名「转账」升级为系统分类（P0-3）----------
    @Test
    fun legacyUpgrade_transferCategoryBecomesSystem() = runBlocking {
        val data = ImportData(
            version = 3,
            categories = listOf(
                CategoryImport(id = 1, name = "转账", type = "EXPENSE", icon = "🔄"),
                CategoryImport(id = 2, name = "餐饮", type = "EXPENSE")
            ),
            transactions = listOf(
                TransactionImport(id = 1, categoryId = 1, amount = 100.0, type = "TRANSFER", date = "2026-04-15T12:00:00")
            )
        )
        manager.importFromJson(gson.toJson(data), db)
        val restored = db.categoryDao().getAllCategories().first()
        val transfer = restored.find { it.name == "转账" }
        assertTrue("「转账」应升级为系统分类", transfer != null && transfer.isSystem)
        val tx = db.transactionDao().getAllSnapshot()
        assertEquals(1, tx.size)
        assertEquals(1L, tx[0].categoryId) // 升格保留原 id，转账交易引用不断裂
    }

    // ---------- 场景⑧：回收站（软删）交易在备份中保留（P1-7）----------
    @Test
    fun recycleBin_preservedAcrossBackup() = runBlocking {
        db.categoryDao().insertAll(listOf(Category(id = 1, name = "餐饮", type = CategoryType.EXPENSE)))
        db.transactionDao().insertAll(listOf(
            Transaction(
                id = 1, categoryId = 1, amountCents = 500, type = TransactionType.EXPENSE,
                date = java.time.LocalDateTime.parse("2026-04-15T12:00:00")
            ),
            Transaction(
                id = 2, categoryId = 1, amountCents = 800, type = TransactionType.EXPENSE,
                date = java.time.LocalDateTime.parse("2026-04-16T12:00:00"),
                deletedAt = java.time.LocalDateTime.parse("2026-04-20T12:00:00")
            )
        ))

        val json = manager.generateExportJson(db)
        val exported = gson.fromJson(json, ImportData::class.java)
        assertEquals(2, exported.transactions.size)
        assertTrue(exported.transactions.any { it.deletedAt != null })

        manager.importFromJson(json, db)
        val all = db.transactionDao().getAllIncludingDeletedSnapshot()
        assertEquals(2, all.size)
        assertTrue("软删交易恢复后仍在回收站", all.any { it.deletedAt != null })
    }

    // ---------- 场景⑨：清除数据重置默认分类并清空标签（P0-2 / P0-1）----------
    @Test
    fun clearAllData_reseedsDefaultsAndClearsTags() = runBlocking {
        db.categoryDao().insertAll(listOf(Category(id = 1, name = "餐饮", type = CategoryType.EXPENSE)))
        db.tagDao().insertAll(listOf(Tag(id = 1, name = "临时")))
        db.transactionDao().insertAll(listOf(
            Transaction(id = 1, categoryId = 1, amountCents = 100, type = TransactionType.EXPENSE, date = java.time.LocalDateTime.parse("2026-04-15T12:00:00"))
        ))

        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val settingsVM = SettingsViewModel(
            app,
            QuickTemplateRepository(db.quickTemplateDao()),
            RecurringTransactionRepository(db.recurringTransactionDao()),
            TransactionRepository(db.transactionDao()),
            CategoryRepository(db.categoryDao()),
            AccountRepository(db.accountDao()),
            BudgetRepository(db.budgetDao()),
            TagRepository(db.tagDao()),
            db
        )
        settingsVM.performClear()

        // 分类应被重置为默认（含系统转账），数量 > 0
        val cats = db.categoryDao().getAllCategories().first()
        assertTrue("清除后应补种默认分类", cats.size > 0)
        // 标签应被清空
        assertEquals(0, db.tagDao().getAll().size)
        // 交易应被清空
        assertEquals(0, db.transactionDao().getAllSnapshot().size)
    }
}
