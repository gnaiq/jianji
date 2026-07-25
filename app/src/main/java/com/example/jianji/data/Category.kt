package com.example.jianji.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "💰",
    val color: String = "#6200EE",
    val type: CategoryType,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val parentId: Long = 0
) {
    val isMajor: Boolean get() = parentId == 0L
}

enum class CategoryType {
    INCOME, EXPENSE
}