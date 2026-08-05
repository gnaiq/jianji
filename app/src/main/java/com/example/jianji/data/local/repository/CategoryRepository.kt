package com.example.jianji.data.local.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
com.example.jianji.data.local.dao.*
com.example.jianji.data.local.entity.*

class CategoryRepository(private val dao: CategoryDao) {
    companion object {
        /** 「未分类」兜底系统分类名（删除普通分类时交易改挂到此） */
        const val UNCLASSIFIED_NAME = "未分类"
    }
    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    suspend fun getBySystemName(name: String): Category? = dao.getBySystemName(name)
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
        // B7-5：删除父分类前，把其直接子分类升为一级（parentId=0），避免子分类变成孤儿
        dao.promoteChildrenToRoot(category.id)
        reassignTransactionsToUncategorized(category.id)
        dao.delete(category)
    }

    /**
     * 设置分类的父节点（B7-2 分类树完整性约束）：
     *  - 禁止自引用（parentId == id）；
     *  - 只允许挂到「一级大类」（parentId 指向的分类自身 parentId 必须为 0），
     *    即分类树最大深度为 2，避免深层级导致环/无限递归；
     *  - 不允许形成环（节点不能挂到自己的后代下，由深度<=2 + 自引用检查共同保证）。
     */
    suspend fun setParent(category: Category, newParentId: Long) {
        if (newParentId == category.id) {
            throw IllegalArgumentException("分类不能把自身设为父分类")
        }
        if (newParentId != 0L) {
            val parent = dao.getById(newParentId)
                ?: throw IllegalArgumentException("父分类不存在")
            // 只允许挂到一级大类；若父分类自身还有父级，则超过深度 2
            if (parent.parentId != 0L) {
                throw IllegalArgumentException("分类树深度超限：只允许两级（大类-小类）")
            }
        }
        dao.update(category.copy(parentId = newParentId))
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

    /** 把某分类下全部交易（含回收站软删记录）改挂「未分类」兜底分类，
     *  避免删分类时子记录外键悬空被 FK 约束拒绝（回收站记录数不变，仅 categoryId 改挂） */
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