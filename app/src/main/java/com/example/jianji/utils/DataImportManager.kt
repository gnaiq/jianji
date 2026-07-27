package com.example.jianji.utils

import android.content.Context
import androidx.room.withTransaction
import com.example.jianji.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

// ===== 备份 JSON 结构（version=2：全量 6 张表；version 缺省视为旧格式，仅含交易+分类）=====

data class TransactionImport(
    val id: Long? = null,
    val categoryId: Long = 0,
    val accountId: Long? = null,
    val amount: Double = 0.0,
    val type: String = "EXPENSE",
    val description: String = "",
    val date: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class CategoryImport(
    val id: Long? = null,
    val name: String = "",
    val type: String = "EXPENSE",
    val icon: String = "💰",
    val color: String = "#6200EE",
    val parentId: Long = 0,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)

data class AccountImport(
    val id: Long? = null,
    val name: String = "",
    val icon: String = "💳",
    val balance: Double = 0.0,
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

data class ImportData(
    val version: Int? = null,
    val transactions: List<TransactionImport> = emptyList(),
    val categories: List<CategoryImport> = emptyList(),
    val accounts: List<AccountImport>? = null,
    val budgets: List<BudgetImport>? = null,
    val recurringTransactions: List<RecurringImport>? = null,
    val quickTemplates: List<TemplateImport>? = null
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
     * 全量备份：读取全部 6 张表并序列化为 JSON（version=2）。
     * 旧备份（仅交易+分类）恢复时仍能兼容（见 importFromJson）。
     */
    suspend fun generateExportJson(db: JianjiDatabase): String = withContext(Dispatchers.IO) {
        val transactions = db.transactionDao().getAllSnapshot()
        val categories = db.categoryDao().getAllCategories().first()
        val accounts = db.accountDao().getAll()
        val budgets = db.budgetDao().getAll()
        val recurring = db.recurringTransactionDao().getAll()
        val templates = db.quickTemplateDao().getAll()

        val data = ImportData(
            version = 2,
            transactions = transactions.map { t ->
                TransactionImport(
                    id = t.id, categoryId = t.categoryId, accountId = t.accountId,
                    amount = t.amount, type = t.type.name, description = t.description,
                    date = t.date.toString(),
                    createdAt = t.createdAt.toString(), updatedAt = t.updatedAt.toString()
                )
            },
            categories = categories.map { c ->
                CategoryImport(
                    id = c.id, name = c.name, type = c.type.name, icon = c.icon,
                    color = c.color, parentId = c.parentId, sortOrder = c.sortOrder,
                    isDefault = c.isDefault
                )
            },
            accounts = accounts.map { a ->
                AccountImport(id = a.id, name = a.name, icon = a.icon, balance = a.balance, isDefault = a.isDefault)
            },
            budgets = budgets.map { b ->
                BudgetImport(
                    id = b.id, categoryId = b.categoryId, amount = b.amount,
                    period = b.period.name, year = b.year, month = b.month
                )
            },
            recurringTransactions = recurring.map { r ->
                RecurringImport(
                    id = r.id, categoryId = r.categoryId, accountId = r.accountId, amount = r.amount,
                    type = r.type.name, description = r.description, frequency = r.frequency.name,
                    interval = r.interval, dayOfMonth = r.dayOfMonth, dayOfWeek = r.dayOfWeek,
                    nextRunDate = r.nextRunDate.toString(), isActive = r.isActive,
                    createdAt = r.createdAt.toString()
                )
            },
            quickTemplates = templates.map { t ->
                TemplateImport(
                    id = t.id, categoryId = t.categoryId, accountId = t.accountId, amount = t.amount,
                    type = t.type.name, description = t.description, sortOrder = t.sortOrder,
                    useCount = t.useCount
                )
            }
        )
        Gson().toJson(data)
    }

    /**
     * 恢复备份：在单一 Room 事务内「清空 → 按原 id 整体重写」，保证原子性——
     * 任意一步失败整笔回滚，绝不会出现「清完账却没有写回」的永久丢失（P0-4）。
     *
     * 兼容策略：
     *  - 全量备份（version==2）：清空并恢复全部 6 张表。
     *  - 旧格式备份（无 accounts 等字段）：仅恢复交易+分类，保留账户/预算/周期/模板，
     *    避免恢复旧备份时意外清空这些表。
     *
     * @return 导入的交易条数；解析失败或为空返回 0
     */
    suspend fun importFromJson(json: String, db: JianjiDatabase): Int = withContext(Dispatchers.IO) {
        val data = parseJson(json) ?: return@withContext 0
        val txs = data.transactions ?: emptyList()
        val cats = data.categories ?: emptyList()
        if (txs.isEmpty() && cats.isEmpty()) return@withContext 0

        val isFull = data.version == 2

        db.withTransaction {
            db.transactionDao().deleteAll()
            db.categoryDao().deleteAll()
            if (isFull) {
                db.accountDao().deleteAll()
                db.budgetDao().deleteAll()
                db.recurringTransactionDao().deleteAll()
                db.quickTemplateDao().deleteAll()
            }

            // 分类（父级优先，保证父->子引用成立）
            db.categoryDao().insertAll(cats.map { c ->
                Category(
                    id = c.id ?: 0,
                    name = c.name,
                    type = if (c.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                    icon = c.icon,
                    color = c.color,
                    parentId = c.parentId,
                    sortOrder = c.sortOrder,
                    isDefault = c.isDefault
                )
            })

            if (isFull) {
                (data.accounts ?: emptyList()).map { a ->
                    Account(id = a.id ?: 0, name = a.name, icon = a.icon, balance = a.balance, isDefault = a.isDefault)
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

                (data.recurringTransactions ?: emptyList()).map { r ->
                    RecurringTransaction(
                        id = r.id ?: 0,
                        categoryId = r.categoryId,
                        accountId = r.accountId,
                        amount = r.amount,
                        type = if (r.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                        description = r.description,
                        frequency = RecurringFrequency.valueOf(r.frequency),
                        interval = r.interval,
                        dayOfMonth = r.dayOfMonth,
                        dayOfWeek = r.dayOfWeek,
                        nextRunDate = LocalDateTime.parse(r.nextRunDate),
                        isActive = r.isActive,
                        createdAt = r.createdAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
                    )
                }.let { db.recurringTransactionDao().insertAll(it) }

                (data.quickTemplates ?: emptyList()).map { t ->
                    QuickTemplate(
                        id = t.id ?: 0,
                        categoryId = t.categoryId,
                        accountId = t.accountId,
                        amount = t.amount,
                        type = if (t.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                        description = t.description,
                        sortOrder = t.sortOrder,
                        useCount = t.useCount
                    )
                }.let { db.quickTemplateDao().insertAll(it) }
            }

            // 交易（最后写入，引用分类/账户已就位）
            db.transactionDao().insertAll(txs.map { t ->
                Transaction(
                    id = t.id ?: 0,
                    categoryId = t.categoryId,
                    accountId = t.accountId,
                    amount = t.amount,
                    type = if (t.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE,
                    description = t.description,
                    date = LocalDateTime.parse(t.date),
                    createdAt = t.createdAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
                    updatedAt = t.updatedAt?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now()
                )
            })
        }
        txs.size
    }
}
