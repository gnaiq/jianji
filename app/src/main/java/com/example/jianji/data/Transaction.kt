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
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["description"]),
        Index(value = ["date"]),
        Index(value = ["deleted_at"]) // 回收站软删：按 deleted_at 非空过滤
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    // §8 金额迁移至 Long 分存储：避免 Double 浮点误差累积（尤其是 SUM 与跨账户转账对账）。
    // 展示层用 amountCents / 100.0，输入层用 (yuan * 100).toLong() 反算。
    val amountCents: Long,
    val type: TransactionType, // INCOME or EXPENSE
    val description: String = "",
    val date: LocalDateTime,
    val accountId: Long? = null,
    val toAccountId: Long? = null, // 转账目标账户（type=TRANSFER 时有效）
    // 回收站/撤销（§5）：软删除标记。null = 未删；非 null = 删除时间，列表/统计/汇总均排除。
    val deletedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER
}
