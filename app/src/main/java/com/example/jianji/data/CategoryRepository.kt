package com.example.jianji.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CategoryRepository(private val dao: CategoryDao) {
    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    fun getCategoriesByType(type: TransactionType): Flow<List<Category>> {
        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        return dao.getCategoriesByType(ct)
    }

    suspend fun insertCategory(category: Category): Long = dao.insert(category)
    suspend fun updateCategory(category: Category) = dao.update(category)
    suspend fun deleteCategory(category: Category) = dao.delete(category)
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
    }
}