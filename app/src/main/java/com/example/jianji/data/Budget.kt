package com.example.jianji.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    // (categoryId, year, month, period) 逻辑唯一；categoryId 为 NULL 时仍参与唯一约束
    // （SQLite 中 NULL 参与 UNIQUE 视为不同值，但本项目总预算至多一条，应用层 upsert 保证不重复）
    indices = [Index(value = ["categoryId", "year", "month", "period"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.NO_ACTION
    )]
)
data class Budget(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long? = null, // null = 总预算
    val amountCents: Long, // 金额，单位：分。避免浮点误差
    val period: BudgetPeriod, // MONTHLY / YEARLY
    val year: Int, // 生效年份
    val month: Int = 0 // period=YEARLY 时忽略
)

enum class BudgetPeriod {
    MONTHLY, YEARLY
}
