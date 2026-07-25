package com.example.jianji.data

/**
 * 应用内置的默认分类（树形结构）。集中定义，供 DatabaseCallback 首次建库与
 * clearAllData() 之后重新种植使用。
 *
 * 默认支出分为四大类（大类 parentId=0，小类 parentId=对应大类 id）：
 *  - 生活类：租房、水电煤气、餐饮、水果、零食、购物、食材、通讯、日用品、电子产品、理发、医疗、运动
 *  - 交通类：公用交通、汽车加油、汽车充电、车辆保养、车辆维修、过路费、停车费、车险、交通违章
 *  - 孩子类：玩具、教育
 *  - 其他类：旅游、社交、人情往来、学习提升
 * 收入保持原有扁平结构。
 */
data class CategorySeed(
    val name: String,
    val icon: String,
    val color: String,
    val type: CategoryType,
    val isDefault: Boolean = true,
    val subs: List<CategorySeed> = emptyList()
)

fun defaultCategoryTree(): List<CategorySeed> = listOf(
    CategorySeed("生活类", "🏠", "#E57373", CategoryType.EXPENSE, subs = listOf(
        CategorySeed("租房", "🏠", "#E57373", CategoryType.EXPENSE),
        CategorySeed("水电煤气", "💡", "#E57373", CategoryType.EXPENSE),
        CategorySeed("餐饮", "🍔", "#E57373", CategoryType.EXPENSE),
        CategorySeed("水果", "🍎", "#E57373", CategoryType.EXPENSE),
        CategorySeed("零食", "🍪", "#E57373", CategoryType.EXPENSE),
        CategorySeed("购物", "🛍️", "#E57373", CategoryType.EXPENSE),
        CategorySeed("食材", "🥬", "#E57373", CategoryType.EXPENSE),
        CategorySeed("通讯", "📱", "#E57373", CategoryType.EXPENSE),
        CategorySeed("日用品", "🧴", "#E57373", CategoryType.EXPENSE),
        CategorySeed("电子产品", "💻", "#E57373", CategoryType.EXPENSE),
        CategorySeed("理发", "✂️", "#E57373", CategoryType.EXPENSE),
        CategorySeed("医疗", "🏥", "#E57373", CategoryType.EXPENSE),
        CategorySeed("运动", "🏃", "#E57373", CategoryType.EXPENSE)
    )),
    CategorySeed("交通类", "🚌", "#42A5F5", CategoryType.EXPENSE, subs = listOf(
        CategorySeed("公用交通", "🚌", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("汽车加油", "⛽", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("汽车充电", "🔌", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("车辆保养", "🔧", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("车辆维修", "🛠️", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("过路费", "🛣️", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("停车费", "🅿️", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("车险", "🛡️", "#42A5F5", CategoryType.EXPENSE),
        CategorySeed("交通违章", "🚨", "#42A5F5", CategoryType.EXPENSE)
    )),
    CategorySeed("孩子类", "👶", "#AB47BC", CategoryType.EXPENSE, subs = listOf(
        CategorySeed("玩具", "🧸", "#AB47BC", CategoryType.EXPENSE),
        CategorySeed("教育", "📚", "#AB47BC", CategoryType.EXPENSE)
    )),
    CategorySeed("其他类", "📦", "#26A69A", CategoryType.EXPENSE, subs = listOf(
        CategorySeed("旅游", "✈️", "#26A69A", CategoryType.EXPENSE),
        CategorySeed("社交", "🤝", "#26A69A", CategoryType.EXPENSE),
        CategorySeed("人情往来", "🎁", "#26A69A", CategoryType.EXPENSE),
        CategorySeed("学习提升", "📖", "#26A69A", CategoryType.EXPENSE)
    )),
    // 收入（保持原有扁平结构）
    CategorySeed("工资", "💼", "#66BB6A", CategoryType.INCOME),
    CategorySeed("奖金", "🎁", "#66BB6A", CategoryType.INCOME),
    CategorySeed("投资收益", "📈", "#66BB6A", CategoryType.INCOME),
    CategorySeed("其他收入", "💰", "#66BB6A", CategoryType.INCOME)
)