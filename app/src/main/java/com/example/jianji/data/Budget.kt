package com.example.jianji.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long? = null, // null = 总预算
    val amount: Double,
    val period: BudgetPeriod, // MONTHLY / YEARLY
    val year: Int, // 生效年份
    val month: Int = 0 // period=YEARLY 时忽略
)

enum class BudgetPeriod {
    MONTHLY, YEARLY
}
