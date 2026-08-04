package com.example.jianji.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * 预算管理 ViewModel：预算设置、删除、进度查询。
 */
class BudgetViewModel(
    private val budgetRepo: BudgetRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    fun getMonthlyBudget(yearMonth: YearMonth): Flow<Double> =
        budgetRepo.observeTotalBudget(yearMonth.year, yearMonth.monthValue)
            .map { it?.amountCents?.toDouble()?.div(100.0) ?: 0.0 }

    suspend fun getMonthlyBudgetEntity(yearMonth: YearMonth): Budget? =
        budgetRepo.getTotalBudget(yearMonth.year, yearMonth.monthValue)

    fun setBudget(budget: Budget) {
        viewModelScope.launch { budgetRepo.setBudget(budget) }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { budgetRepo.delete(budget) }
    }

    suspend fun getBudgetProgress(year: Int, month: Int, budget: Budget?, categoryId: Long? = null): BudgetProgress {
        return transactionRepository.getBudgetProgress(year, month, budget, categoryId)
    }

    fun deleteAll() {
        viewModelScope.launch { budgetRepo.deleteAll() }
    }
}