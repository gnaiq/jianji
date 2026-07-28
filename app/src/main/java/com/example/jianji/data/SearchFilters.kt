package com.example.jianji.data

import java.time.LocalDateTime

/**
 * 历史/搜索的多维过滤条件（§1 P1 搜索过滤增强）。
 * 全部字段可选：null / 空串 表示「不限制该维度」。
 */
data class SearchFilters(
    val text: String = "",
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    // 日期区间语义 [startDate, endDate)，与现有 date 范围查询一致
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null
) {
    val isEmpty: Boolean
        get() = text.isBlank() && type == null && accountId == null &&
                minAmount == null && maxAmount == null &&
                startDate == null && endDate == null
}

/**
 * 在内存列表上应用 [SearchFilters]（复用已加载的全量交易，避免重复查询）。
 * - text：匹配描述或分类名（忽略大小写）
 * - type / accountId：精确匹配
 * - minAmount / maxAmount：金额闭区间
 * - startDate / endDate：日期半开区间 [start, end)
 * 结果按 date DESC 排序，与原列表展示顺序一致。
 */
fun List<Transaction>.applySearchFilters(
    filters: SearchFilters,
    categoryMap: Map<Long, Category>
): List<Transaction> {
    return filter { tx ->
        if (filters.text.isNotBlank()) {
            val catName = categoryMap[tx.categoryId]?.name
            val hit = catName?.contains(filters.text, ignoreCase = true) == true ||
                    tx.description.contains(filters.text, ignoreCase = true)
            if (!hit) return@filter false
        }
        if (filters.type != null && tx.type != filters.type) return@filter false
        if (filters.accountId != null && tx.accountId != filters.accountId) return@filter false
        if (filters.minAmount != null && tx.amount < filters.minAmount) return@filter false
        if (filters.maxAmount != null && tx.amount > filters.maxAmount) return@filter false
        if (filters.startDate != null && tx.date < filters.startDate) return@filter false
        if (filters.endDate != null && tx.date >= filters.endDate) return@filter false
        true
    }.sortedByDescending { it.date }
}
