package com.example.jianji.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val type: TransactionType,
    val description: String = "",
    val accountId: Long? = null,
    val frequency: RecurringFrequency,
    val interval: Int = 1, // 每 N 个 frequency 执行一次
    val dayOfMonth: Int = 1, // MONTHLY/YEARLY: 几号
    val dayOfWeek: Int = 1, // WEEKLY: 周一=1...周日=7
    val nextRunDate: LocalDateTime, // 下次执行日期
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class RecurringFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}
