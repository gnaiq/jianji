package com.example.jianji.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // date 索引（§1 P0）：首页/历史/统计的日期范围过滤与 SUM 聚合均按 date 检索，
    // 万条数据下无索引即全表扫描；与 MIGRATION_4_5 的 CREATE INDEX 配对
    indices = [Index(value = ["accountId"]), Index(value = ["description"]), Index(value = ["date"])]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val type: TransactionType, // INCOME or EXPENSE
    val description: String = "",
    val date: LocalDateTime,
    val accountId: Long? = null,
    val toAccountId: Long? = null, // 转账目标账户（type=TRANSFER 时有效）
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER
}
