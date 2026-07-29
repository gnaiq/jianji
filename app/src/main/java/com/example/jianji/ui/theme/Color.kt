package com.example.jianji.ui.theme

import androidx.compose.ui.graphics.Color

// 靛蓝 + 青绿 冷调主题（替代默认 Purple 调色板）
val IndigoLight = Color(0xFF4F46E5) // primary  靛蓝 600
val TealLight = Color(0xFF14B8A6)   // secondary 青绿 500
val VioletLight = Color(0xFF8B5CF6) // tertiary 紫   500

val IndigoDark = Color(0xFFA5B4FC)  // primary  靛蓝 300
val TealDark = Color(0xFF5EEAD4)    // secondary 青绿 300
val VioletDark = Color(0xFFC4B5FD)  // tertiary 紫   300

// === 语义色 Design Tokens ===
// 统一管理全局颜色，消除硬编码魔法数字，为暗黑模式/多主题铺路
object AppColors {
    // 交易类型语义色
    val IncomeGreen = Color(0xFF4CAF50)
    val ExpenseRed = Color(0xFFF44336)
    val BalanceBlue = Color(0xFF2196F3)
    val BalanceNegative = Color(0xFFFF5722)

    // 预算进度条
    val BudgetSafe = Color(0xFF4CAF50)
    val BudgetWarning = Color(0xFFFF9800)
    val BudgetOverrun = Color(0xFFF44336)

    // 图表色
    val ChartExpense = Color(0xFFF44336)
    val ChartIncome = Color(0xFF4CAF50)
    val ChartBalance = Color(0xFF2196F3)
    val ChartTransfer = Color(0xFF8B5CF6) // tertiary

    // 组件色
    val DeleteRed = Color(0xFFF44336)  // 删除按钮红
    val TransferBg = Color(0xFF8B5CF6) // 转账背景

    // 兼容旧引用（逐步迁移）
    val IncomeGreenLegacy = IncomeGreen
    val ExpenseRedLegacy = ExpenseRed
}

// 逐渐弃用，请使用 AppColors.IncomeGreen / AppColors.ExpenseRed
@Deprecated("Use AppColors.IncomeGreen", ReplaceWith("AppColors.IncomeGreen"))
val IncomeGreen = AppColors.IncomeGreen
@Deprecated("Use AppColors.ExpenseRed", ReplaceWith("AppColors.ExpenseRed"))
val ExpenseRed = AppColors.ExpenseRed