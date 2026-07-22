package com.example.jianji.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String = "💰", // Emoji or icon name
    val color: String = "#6200EE", // Hex color
    val type: CategoryType, // INCOME or EXPENSE
    val isDefault: Boolean = false
)

enum class CategoryType {
    INCOME, EXPENSE
}
