package com.example.jianji.utils

import com.example.jianji.data.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

data class ImportData(
    val transactions: List<TransactionImport> = emptyList(),
    val categories: List<CategoryImport> = emptyList()
)

data class TransactionImport(
    val categoryId: Long,
    val amount: Double,
    val type: String,
    val description: String = "",
    val date: String
)

data class CategoryImport(
    val id: Long = 0,
    val name: String,
    val type: String,
    val icon: String = "📁",
    val color: String = "#6200EE",
    val parentId: Long = 0,
    val sortOrder: Int = 0
)

class DataImportManager {
    suspend fun parseJson(json: String): ImportData? = withContext(Dispatchers.IO) {
        try {
            Gson().fromJson(json, ImportData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importFromJson(
        json: String,
        transactionRepo: TransactionRepository,
        categoryRepo: CategoryRepository
    ): Int = withContext(Dispatchers.IO) {
        val data = parseJson(json) ?: return@withContext 0
        var count = 0

        // 是否为新格式备份（含真实 id，可精确映射层级与交易分类引用）
        val hasIds = data.categories.any { it.id != 0L }
        // 旧 id -> 名称（用于交易分类 id 重新映射）
        val oldIdToName = data.categories.associate { it.id to it.name }

        val oldToNew = mutableMapOf<Long, Long>()
        val nameToNew = mutableMapOf<String, Long>()
        val pendingParent = mutableListOf<Pair<Long, Long>>() // (newId, oldParentId)

        for (ci in data.categories) {
            val type = if (ci.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
            val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
            val existing = categoryRepo.getAllCategories().first()
                .firstOrNull { it.name == ci.name && it.type == ct }
            val newId = if (existing != null) {
                existing.id
            } else {
                categoryRepo.insertCategory(
                    Category(
                        name = ci.name, type = ct, icon = ci.icon,
                        color = ci.color, sortOrder = ci.sortOrder, parentId = 0
                    )
                )
            }
            oldToNew[ci.id] = newId
            nameToNew[ci.name] = newId
            if (existing == null && ci.parentId != 0L) {
                pendingParent.add(newId to ci.parentId)
            }
        }

        // 解析小类的 parentId（仅新格式需映射；旧格式父级为 0，保持扁平）
        if (hasIds) {
            for ((newId, oldParentId) in pendingParent) {
                val parentName = oldIdToName[oldParentId]
                val newParentId = if (parentName != null) nameToNew[parentName] else null
                if (newParentId != null) {
                    val cat = categoryRepo.getAllCategories().first().first { it.id == newId }
                    categoryRepo.updateCategory(cat.copy(parentId = newParentId))
                }
            }
        }

        // 恢复 = 替换：先清空现有交易，再按备份重新写入，避免重复叠加
        transactionRepo.deleteAll()

        for (ti in data.transactions) {
            try {
                val date = LocalDateTime.parse(ti.date)
                val type = if (ti.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                // 新格式按 id 映射；旧格式沿用原 id（依赖同名分类已存在）
                val categoryId = if (hasIds) oldToNew[ti.categoryId] ?: ti.categoryId else ti.categoryId
                transactionRepo.insertTransaction(
                    Transaction(
                        categoryId = categoryId,
                        amount = ti.amount,
                        type = type,
                        description = ti.description,
                        date = date
                    )
                )
                count++
            } catch (_: Exception) { }
        }
        count
    }

    fun generateExportJson(
        transactions: List<Transaction>,
        categories: List<Category>
    ): String {
        val txs = transactions.map { tx ->
            TransactionImport(
                categoryId = tx.categoryId,
                amount = tx.amount,
                type = tx.type.name,
                description = tx.description,
                date = tx.date.toString()
            )
        }
        val cats = categories.map { cat ->
            CategoryImport(
                id = cat.id,
                name = cat.name,
                type = cat.type.name,
                icon = cat.icon,
                color = cat.color,
                parentId = cat.parentId,
                sortOrder = cat.sortOrder
            )
        }
        return Gson().toJson(ImportData(transactions = txs, categories = cats))
    }
}