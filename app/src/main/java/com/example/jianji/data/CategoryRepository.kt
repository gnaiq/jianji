package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CategoryRepository(private val dao: CategoryDao) {
    companion object {
        /** 「未分类」兜底系统分类名（删除普通分类时交易改挂到此） */
        const val UNCLASSIFIED_NAME = "未分类"
    }
    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    fun getCategoriesByType(type: TransactionType): Flow<List<Category>> {
        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        return dao.getCategoriesByType(ct)
    }

    suspend fun insertCategory(category: Category): Long = dao.insert(category)
    suspend fun updateCategory(category: Category) = dao.update(category)
    suspend fun deleteCategory(category: Category) {
        // 修复 B1-4/B5-6：禁止删除系统分类（如「转账」，被转账交易引用）；
        // 普通分类删除前，把其下「可见」交易改挂「未分类」兜底分类，回收站记录保留不动。
        if (category.isSystem) return
        reassignTransactionsToUncategorized(category.id)
        dao.delete(category)
    }

    /**
     * 保证存在「未分类」系统兜底分类，返回其 id。删除普通分类时把交易改挂到此分类，
     * 避免交易丢失（修复 B1-4 转账误删 / B5-6 回收站穿透删除）。
     */
    suspend fun ensureUncategorized(): Long {
        val existing = dao.getBySystemName(UNCLASSIFIED_NAME)
        if (existing != null) return existing.id
        return dao.insert(
            Category(
                name = UNCLASSIFIED_NAME,
                icon = "❓",
                color = "#9E9E9E",
                type = CategoryType.EXPENSE,
                isDefault = false,
                isSystem = true,
                sortOrder = 998,
                parentId = 0
            )
        )
    }

    /** 把某分类下「可见」交易改挂「未分类」（回收站软删记录不动） */
    suspend fun reassignTransactionsToUncategorized(categoryId: Long) {
        val target = ensureUncategorized()
        dao.reassignCategory(categoryId, target)
    }
    suspend fun getDefaultCategories(): List<Category> = dao.getDefaultCategories()
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getCount(): Int = dao.getCount()
    suspend fun getMaxSortOrder(): Int = dao.getMaxSortOrder()

    /** 同一层级（同一大类下 / 同一类型扁平列表）的有序兄弟节点，用于手动排序 */
    suspend fun getSiblings(type: CategoryType, parentId: Long): List<Category> =
        dao.getSiblings(type, parentId)

    /** 手动排序：将 category 在其同级中上移(-1)或下移(+1) */
    suspend fun moveCategory(category: Category, delta: Int) {
        val siblings = dao.getSiblings(category.type, category.parentId)
        val idx = siblings.indexOfFirst { it.id == category.id }
        if (idx < 0) return
        val newIdx = idx + delta
        if (newIdx < 0 || newIdx >= siblings.size) return
        val other = siblings[newIdx]
        val a = category.sortOrder
        val b = other.sortOrder
        dao.update(category.copy(sortOrder = b))
        dao.update(other.copy(sortOrder = a))
    }

    /** 首次建库 / 清空数据后种植默认分类（含大类-小类两级结构） */
    suspend fun seedDefaults() {
        if (dao.getCount() > 0) return
        var majorOrder = 0
        for (node in defaultCategoryTree()) {
            val majorId = dao.insert(
                Category(
                    name = node.name,
                    icon = node.icon,
                    color = node.color,
                    type = node.type,
                    isDefault = node.isDefault,
                    sortOrder = majorOrder,
                    parentId = 0
                )
            )
            majorOrder++
            node.subs.forEachIndexed { i, sub ->
                dao.insert(
                    Category(
                        name = sub.name,
                        icon = sub.icon,
                        color = sub.color,
                        type = sub.type,
                        isDefault = sub.isDefault,
                        sortOrder = i,
                        parentId = majorId
                    )
                )
            }
        }
        // 系统分类：转账（供账户间转账引用，不在用户分类列表展示）
        dao.insert(
            Category(
                name = "转账",
                icon = "🔄",
                color = "#6200EE",
                type = CategoryType.EXPENSE,
                isSystem = true,
                sortOrder = 999,
                parentId = 0
            )
        )
    }
}