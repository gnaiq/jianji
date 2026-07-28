package com.example.jianji.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 标签（§11 标签系统）：可附加到任意交易，用于多维度归类（与分类正交）。
 * 通过 [TransactionTagCrossRef] 与交易建立多对多关系。
 */
@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String = "#6200EE",
    val icon: String = "🏷️",
    val sortOrder: Int = 0
)
