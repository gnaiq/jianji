package com.example.jianji.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_templates")
data class QuickTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val type: TransactionType,
    val description: String = "",
    val accountId: Long? = null,
    val sortOrder: Int = 0,
    val useCount: Int = 0
)
