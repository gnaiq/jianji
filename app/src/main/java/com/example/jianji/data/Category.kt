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
    val isSystem: Boolean = false, // 系统分类（如转账），不在用户分类列表中展示
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val parentId: Long = 0
) {
    val isMajor: Boolean get() = parentId == 0L
}

enum class CategoryType {
    INCOME, EXPENSE
}