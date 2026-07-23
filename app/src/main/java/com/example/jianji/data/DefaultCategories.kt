package com.example.jianji.data

/**
 * 应用内置的默认分类。集中定义，供 DatabaseCallback 首次建库与
 * clearAllData() 之后重新种植使用，避免重复维护两份列表。
 * 与首次建库的种子保持一致（颜色取默认值 #6200EE）。
 */
fun createDefaultCategories(): List<Category> = listOf(
    // 收入
    Category(name = "工资", icon = "💼", type = CategoryType.INCOME, isDefault = true),
    Category(name = "奖金", icon = "🎁", type = CategoryType.INCOME, isDefault = true),
    Category(name = "投资收益", icon = "📈", type = CategoryType.INCOME, isDefault = true),
    Category(name = "其他收入", icon = "💰", type = CategoryType.INCOME, isDefault = true),
    // 支出
    Category(name = "食物", icon = "🍔", type = CategoryType.EXPENSE, isDefault = true),
    Category(name = "交通", icon = "🚗", type = CategoryType.EXPENSE, isDefault = true),
    Category(name = "娱乐", icon = "🎮", type = CategoryType.EXPENSE, isDefault = true),
    Category(name = "购物", icon = "🛍️", type = CategoryType.EXPENSE, isDefault = true),
    Category(name = "医疗", icon = "🏥", type = CategoryType.EXPENSE, isDefault = true),
    Category(name = "教育", icon = "📚", type = CategoryType.EXPENSE, isDefault = true),
    Category(name = "其他支出", icon = "💸", type = CategoryType.EXPENSE, isDefault = true)
)
