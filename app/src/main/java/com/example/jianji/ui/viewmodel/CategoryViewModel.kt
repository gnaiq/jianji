package com.example.jianji.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 分类管理 ViewModel：分类 CRUD、排序、移动。
 * 从 TransactionViewModel 拆分，遵循单一职责原则。
 */
class CategoryViewModel(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<Category>> = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<Category>> = categoryRepository.getCategoriesByType(TransactionType.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String, icon: String = "📁", type: CategoryType, parentId: Long = 0, color: String = "#6200EE") {
        viewModelScope.launch {
            val siblings = categoryRepository.getSiblings(type, parentId)
            val order = siblings.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
            categoryRepository.insertCategory(
                Category(name = name, type = type, icon = icon, sortOrder = order, parentId = parentId, color = color)
            )
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { categoryRepository.updateCategory(category) }
    }

    fun moveCategory(category: Category, delta: Int) {
        viewModelScope.launch { categoryRepository.moveCategory(category, delta) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            if (category.parentId == 0L) {
                categoryRepository.getSiblings(category.type, category.id).forEach {
                    categoryRepository.deleteCategory(it)
                }
            }
            categoryRepository.deleteCategory(category)
        }
    }

    fun seedDefaults() {
        viewModelScope.launch { categoryRepository.seedDefaults() }
    }

    fun deleteAll() {
        viewModelScope.launch { categoryRepository.deleteAll() }
    }
}