package com.example.jianji.utils

import android.content.Context
import com.example.jianji.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

// ===== 备份 JSON 结构 =====
// version=4：全量 8 张表（含 isSystem 系统分类 + 回收站软删交易 deletedAt）
// version=3：全量 8 张表（6 张 + tags + transaction_tags）
// version=2：全量 6 张表（无标签，恢复时标签表被清空后保持为空）
// version 缺省：旧格式，仅含交易+分类

data class TransactionImport(
    val id: Long? = null,
    val categoryId: Long = 0,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val description: String = "",
    val date: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val deletedAt: String? = null
)

data class CategoryImport(
    val id: Long? = null,
    val name: String = "",
    val type: String = "EXPENSE",
    val icon: String = "💰",
    val color: String = "#6200EE",
    val parentId: Long = 0,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val isSystem: Boolean = false
)

data class AccountImport(
    val id: Long? = null,
    val name: String = "",
    val icon: String = "💳",
    val isDefault: Boolean = false
)

data class BudgetImport(
    val id: Long? = null,
    val categoryId: Long? = null,
    val amount: Double = 0.0,
    val period: String = "MONTHLY",
    val year: Int = 0,
    val month: Int = 0
)

data class RecurringImport(
    val id: Long? = null,
    val categoryId: Long = 0,
    val accountId: Long? = null,
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val description: String = "",
    val frequency: String = "MONTHLY",
    val interval: Int = 1,
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val dayOfWeek: Int = 1,
    val nextRunDate: String = "",
    val isActive: Boolean = true,
    val createdAt: String? = null
)

data class TemplateImport(
    val id: Long? = null,
    val categoryId: Long = 0,
    val accountId: Long? = null,
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val description: String = "",
    val sortOrder: Int = 0,
    val useCount: Int = 0
)

data class TagImport(
    val id: Long? = null,
    val name: String = "",
    val color: String = "#6200EE",
    val icon: String = "🏷️",
    val sortOrder: Int = 0
)

/** 交易↔标签关联（多对多），恢复时按 id 原样重建 */
data class TransactionTagImport(
    val transactionId: Long = 0,
    val tagId: Long = 0
)

data class ImportData(
    val version: Int? = null,
    val transactions: List<TransactionImport> = emptyList(),
    val categories: List<CategoryImport> = emptyList(),
    val accounts: List<AccountImport>? = null,
    val budgets: List<BudgetImport>? = null,
    val recurringTransactions: List<RecurringImport>? = null,
    val quickTemplates: List<TemplateImport>? = null,
    val tags: List<TagImport>? = null,
    val transactionTags: List<TransactionTagImport>? = null
)

/** 恢复结果：成功导入交易条数 + 是否为全量恢复 + 跳过的无效记录数 + 导入的分类数 */
data class ImportResult(
    val transactionCount: Int,
    val isFullRestore: Boolean,
    val skippedCount: Int = 0,
    val categoryCount: Int = 0
)

class DataImportManager {
    suspend fun parseJson(json: String): ImportData? = withContext(Dispatchers.IO) {
        try {
            Gson().fromJson(json, ImportData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 全量备份：读取全部 8 张表并序列化为 JSON。
     * - version 自 4 起含 isSystem（系统分类如「转账」标记）+ deletedAt（回收站软删交易），
     *   保证恢复后系统分类不被误当普通分类、回收站不丢（P0-3 / P1-7）。
     * - 含回收站：用 getAllIncludingDeletedSnapshot 而非 getAllSnapshot，软删交易一并导出。
     * 旧备份（version<4 无 isSystem / 无 version 仅交易+分类）恢复时仍能兼容（见 importFromJson）。
     */
    suspend fun generateExportJson(db: JianjiDatabase): String = withContext(Dispatchers.IO) {
        val transactions = db.transactionDao().getAllIncludingDeletedSnapshot()
        val categories = db.categoryDao().getAllCategories().first()
        val accounts = db.accountDao().getAll()
        val budgets = db.budgetDao().getAll()
        val recurring = db.recurringTransactionDao().getAll()
        val templates = db.quickTemplateDao().getAll()
        val tags = db.tagDao().getAll()
        val crossRefs = db.tagDao().getAllCrossRefs()

        val data = ImportData(
            version = 4,
            transactions = transactions.map { t ->
                TransactionImport(
                    id = t.id, categoryId = t.categoryId, accountId = t.accountId,
                    toAccountId = t.toAccountId,
                    amount = t.amountCents / 100.0, type = t.type.name, description = t.description,
                    date = t.date.toString(),
                    createdAt = t.createdAt.toString(), updatedAt = t.updatedAt.toString(),
                    deletedAt = t.deletedAt?.toString()
                )
            },
            categories = categories.map { c ->
                CategoryImport(
                    id = c.id, name = c.name, type = c.type.name, icon = c.icon,
                    color = c.color, parentId = c.parentId, sortOrder = c.sortOrder,
                    isDefault = c.isDefault, isSystem = c.isSystem
                )
            },
            accounts = accounts.map { a ->
                AccountImport(id = a.id, name = a.name, icon = a.icon, isDefault = a.isDefault)
            },
            budgets = budgets.map { b ->
                BudgetImport(
                    id = b.id, categoryId = b.categoryId, amount = b.amount,
                    period = b.period.name, year = b.year, month = b.month
                )
            },
            recurringTransactions = recurring.map { r ->
                RecurringImport(
                    id = r.id, categoryId = r.categoryId, accountId = r.accountId, amount = r.amountCents / 100.0,
                    type = r.type.name, description = r.description, frequency = r.frequency.name,
                    interval = r.interval, dayOfMonth = r.dayOfMonth, monthOfYear = r.monthOfYear,
                    dayOfWeek = r.dayOfWeek,
                    nextRunDate = r.nextRunDate.toString(), isActive = r.isActive,
                    createdAt = r.createdAt.toString()
                )
            },
            quickTemplates = templates.map { t ->
                TemplateImport(
                    id = t.id, categoryId = t.categoryId, accountId = t.accountId, amount = t.amountCents / 100.0,
                    type = t.type.name, description = t.description, sortOrder = t.sortOrder,
                    useCount = t.useCount
                )
            },
            tags = tags.map { t ->
                TagImport(id = t.id, name = t.name, color = t.color, icon = t.icon, sortOrder = t.sortOrder)
            },
            transactionTags = crossRefs.map { c ->
                TransactionTagImport(transactionId = c.transactionId, tagId = c.tagId)
            }
        )
        Gson().toJson(data)
    }

    /**
     * 恢复备份：在单一 Room 事务内「清空 → 按原 id 整体重写」，保证原子性——
     * 任意一步失败整笔回滚，绝不会出现「清完账却没有写回」的永久丢失。
     *
     * 兼容策略：
     *  - 全量备份（version>=2）：清空并恢复全部表。version=2 的旧全量备份不含标签，
     *    其 tags/transactionTags 为 null，恢复后标签表为空（与备份内容一致）。
     *  - 旧格式备份（无 version 字段）：仅恢复交易+分类，保留账户/预算/周期/模板/标签，
     *    避免恢复旧备份时意外清空这些表。
     *
     * 系统分类（isSystem，如「转账」）处理（P0-3）：
     *  - version>=4 备份：isSystem 随备份原样恢复。
     *  - 旧备份（version<4，无 isSystem 字段）：同名「转账」分类原地升格为 isSystem=true，
     *    保留其原 id，转账交易的分类引用不断裂；仅当备份里完全没有「转账」分类时，
     *    才新建一个系统转账分类兜底，确保转账功能可用。
     *
     * @return 导入的交易条数、分类数；解析失败或为空返回 0
     */
    suspend fun importFromJson(
        json: String,
        db: JianjiDatabase,
        context: Context? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        // 修复 P6-1：透明解密——若为加密备份（"v1:" 前缀）则用备份口令解密，
        // 失败抛异常交由上层提示用户；明文旧备份原样通过，向下兼容。
        val plain = if (BackupCrypto.isEncrypted(json)) {
            val pass = if (context != null) AppPrefs.getBackupPassphrase(context) else ""
            BackupCrypto.decrypt(json, pass)
        } else {
            json
        }
        val data = parseJson(plain) ?: return@withContext ImportResult(0, false)
        val txs = data.transactions
        val cats = data.categories
        if (txs.isEmpty() && cats.isEmpty()) return@withContext ImportResult(0, false)

        // 判定放宽为 >=2：新增 version=4（含标签与系统分类）后仍须走全量恢复分支，
        // 写死 ==2 会让 v3/v4 备份被当成旧格式，只恢复交易+分类（数据丢失）
        val isFull = (data.version ?: 0) >= 2
        // 无效记录（日期/类型非法）逐条跳过并计数，避免单条坏数据导致整笔导入回滚
        var skipped = 0
        val validTxs = mutableListOf<Transaction>()
        var restoredCategoryCount = 0

        db.withTransaction {
            db.transactionDao().deleteAll()
            db.categoryDao().deleteAll()
            if (isFull) {
                db.accountDao().deleteAll()
                db.budgetDao().deleteAll()
                db.recurringTransactionDao().deleteAll()
                db.quickTemplateDao().deleteAll()
                // 标签表清空由外键 CASCADE 连带清理 transaction_tags
                db.tagDao().deleteAll()
            }

            // 分类（父级优先，保证父->子引用成立）
            // 旧备份（version < 4）未导出 isSystem：转账系统分类会被误当作普通分类导入，
            // 既暴露给用户可误删（级联破坏转账），又让转账功能找不到系统分类。
            // 升级策略：同名「转账」记录原地升格为 isSystem=true（保留原 id，转账交易引用不断裂）；
            // 仅当备份里完全没有「转账」分类时，才新建一个系统转账分类兜底（P0-3）。
            val legacyMissingIsSystem = (data.version ?: 0) < 4
            val transferName = "转账"
            var sawTransfer = false
            val categoryEntities = cats.map { c ->
                val isSys = if (legacyMissingIsSystem && c.name == transferName) {
                    sawTransfer = true
                    true
                } else {
                    c.isSystem
                }
                Category(
                    id = c.id ?: 0,
                    name = c.name,
                    type = if (c.type == "INCOME") CategoryType.INCOME else CategoryType.EXPENSE,
                    icon = c.icon,
                    color = c.color,
                    parentId = c.parentId,
                    sortOrder = c.sortOrder,
                    isDefault = c.isDefault,
                    isSystem = isSys
                )
            }
            val finalCategories = if (legacyMissingIsSystem && !sawTransfer) {
                categoryEntities + Category(
                    id = 0, name = transferName,
                    icon = "🔄", color = "#6200EE",
                    type = CategoryType.EXPENSE, isSystem = true, sortOrder = 999, parentId = 0
                )
            } else {
                categoryEntities
            }
            db.categoryDao().insertAll(finalCategories)
            restoredCategoryCount = finalCategories.size

            if (isFull) {
                (data.accounts ?: emptyList()).map { a ->
                    Account(id = a.id ?: 0, name = a.name, icon = a.icon, isDefault = a.isDefault)
                }.let { db.accountDao().insertAll(it) }

                (data.budgets ?: emptyList()).map { b ->
                    Budget(
                        id = b.id ?: 0,
                        categoryId = b.categoryId,
                        amount = b.amount,
                        period = if (b.period == "YEARLY") BudgetPeriod.YEARLY else BudgetPeriod.MONTHLY,
                        year = b.year,
                        month = b.month
                    )
                }.let { db.budgetDao().insertAll(it) }

                // 周期交易：非法周期/日期逐条跳过并计数，与交易记录同款容错，避免单条坏数据令整笔回滚
                val validRecurring = mutableListOf<RecurringTransaction>()
                for (r in (data.recurringTransactions ?: emptyList())) {
                    try {
                        validRecurring.add(
                            RecurringTransaction(
                                id = r.id ?: 0,
                                categoryId = r.categoryId,
                                accountId = r.accountId,
                                amountCents = Math.round(r.amount * 100),
                                type = if (r.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                                description = r.description,
                                frequency = RecurringFrequency.valueOf(r.frequency),
                                interval = r.interval,
                                dayOfMonth = r.dayOfMonth,
                                monthOfYear = r.monthOfYear,
                                dayOfWeek = r.dayOfWeek,
                                nextRunDate = LocalDateTime.parse(r.nextRunDate),
                                isActive = r.isActive,
                                createdAt = r.createdAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
                            )
                        )
                    } catch (e: Exception) {
                        skipped++
                    }
                }
                if (validRecurring.isNotEmpty()) db.recurringTransactionDao().insertAll(validRecurring)

                (data.quickTemplates ?: emptyList()).map { t ->
                    QuickTemplate(
                        id = t.id ?: 0,
                        categoryId = t.categoryId,
                        accountId = t.accountId,
                        amountCents = Math.round(t.amount * 100),
                        type = if (t.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                        description = t.description,
                        sortOrder = t.sortOrder,
                        useCount = t.useCount
                    )
                }.let { db.quickTemplateDao().insertAll(it) }

                (data.tags ?: emptyList()).map { t ->
                    Tag(id = t.id ?: 0, name = t.name, color = t.color, icon = t.icon, sortOrder = t.sortOrder)
                }.let { if (it.isNotEmpty()) db.tagDao().insertAll(it) }
            }

            // 交易（最后写入，引用分类/账户已就位）；非法记录跳过并计数。
            // deletedAt 一并恢复，回收站软删交易恢复后仍在回收站（P1-7）。
            for (t in txs) {
                try {
                    val date = LocalDateTime.parse(t.date)
                    val type = when (t.type) {
                        "INCOME" -> TransactionType.INCOME
                        "TRANSFER" -> TransactionType.TRANSFER
                        "EXPENSE" -> TransactionType.EXPENSE
                        else -> throw IllegalArgumentException("unknown type: ${t.type}")
                    }
                    validTxs.add(
                        Transaction(
                            id = t.id ?: 0,
                            categoryId = t.categoryId,
                            accountId = t.accountId,
                            toAccountId = t.toAccountId,
                            amountCents = Math.round(t.amount * 100),
                            type = type,
                            description = t.description,
                            date = date,
                            createdAt = t.createdAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
                            updatedAt = t.updatedAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
                            deletedAt = t.deletedAt?.let { LocalDateTime.parse(it) }
                        )
                    )
                } catch (e: Exception) {
                    skipped++
                }
            }
            if (validTxs.isNotEmpty()) db.transactionDao().insertAll(validTxs)

            // 交易-标签关联最后写入（外键要求交易与标签均已就位）。
            // 仅保留双端 id 都存在的关联，跳过的坏交易/缺失标签不会引发外键约束失败。
            if (isFull) {
                val txIds = validTxs.mapTo(HashSet()) { it.id }
                val tagIds = (data.tags ?: emptyList()).mapNotNullTo(HashSet()) { it.id }
                val refs = (data.transactionTags ?: emptyList())
                    .filter { it.transactionId in txIds && it.tagId in tagIds }
                    .map { TransactionTagCrossRef(it.transactionId, it.tagId) }
                if (refs.isNotEmpty()) db.tagDao().insertCrossRefs(refs)
            }
        }
        ImportResult(validTxs.size, isFull, skipped, restoredCategoryCount)
    }
}
