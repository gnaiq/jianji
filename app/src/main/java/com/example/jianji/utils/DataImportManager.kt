package com.example.jianji.utils

import android.content.Context
import com.example.jianji.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
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
    val name: String,
    val type: String,
    val icon: String = "📁"
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

        // 导入分类
        val existingCats = mutableListOf<Category>()
        categoryRepo.getAllCategories().collect { existingCats.addAll(it) }

        val catNameMap = mutableMapOf<String, Long>()
        for (ci in data.categories) {
            val existing = existingCats.find { it.name == ci.name }
            if (existing != null) {
                catNameMap[ci.name] = existing.id
            } else {
                val type = if (ci.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                val id = categoryRepo.insertCategory(Category(name = ci.name, type = ct, icon = ci.icon))
                catNameMap[ci.name] = id
            }
        }

        // 导入交易
        for (ti in data.transactions) {
            try {
                val date = LocalDateTime.parse(ti.date)
                val type = if (ti.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
                transactionRepo.insertTransaction(
                    Transaction(
                        categoryId = ti.categoryId,
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
            CategoryImport(name = cat.name, type = cat.type.name, icon = cat.icon)
        }
        return Gson().toJson(ImportData(transactions = txs, categories = cats))
    }
}
