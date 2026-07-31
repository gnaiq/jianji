package com.example.jianji.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_templates")
data class QuickTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    val type: TransactionType,
    val description: String = "",
    val accountId: Long? = null,
    val sortOrder: Int = 0,
    val useCount: Int = 0
)
