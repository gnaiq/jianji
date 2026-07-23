package com.example.jianji.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.Category
import com.example.jianji.data.CategoryRepository
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionRepository
import com.example.jianji.data.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TransactionViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _monthlyIncome = MutableStateFlow(0.0)
    val monthlyIncome: StateFlow<Double> = _monthlyIncome.asStateFlow()

    private val _monthlyExpense = MutableStateFlow(0.0)
    val monthlyExpense: StateFlow<Double> = _monthlyExpense.asStateFlow()

    private val _dailyExpense = MutableStateFlow(0.0)
    val dailyExpense: StateFlow<Double> = _dailyExpense.asStateFlow()

    init {
        loadTransactions()
        loadCategories()
        updateMonthlyStats()
        updateDailyStats()
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            transactionRepository.getAllTransactions().collect {
                _transactions.value = it
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect {
                _categories.value = it
            }
        }
    }

    fun addTransaction(categoryId: Long, amount: Double, type: TransactionType, description: String, date: LocalDateTime) {
        viewModelScope.launch {
            val transaction = Transaction(
                categoryId = categoryId,
                amount = amount,
                type = type,
                description = description,
                date = date
            )
            transactionRepository.insertTransaction(transaction)
            updateMonthlyStats()
            updateDailyStats()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction)
            updateMonthlyStats()
            updateDailyStats()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
            updateMonthlyStats()
            updateDailyStats()
        }
    }

    fun addCategory(name: String, icon: String, type: com.example.jianji.data.CategoryType) {
        viewModelScope.launch {
            val category = Category(name = name, icon = icon, type = type)
            categoryRepository.insertCategory(category)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }

    fun updateDailyStats(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val startDate = date.atStartOfDay()
            val endDate = date.atTime(23, 59, 59)
            val expense = transactionRepository.getSumByType(TransactionType.EXPENSE, startDate, endDate)
            _dailyExpense.value = expense
        }
    }

    fun updateMonthlyStats(yearMonth: YearMonth = YearMonth.now()) {
        viewModelScope.launch {
            val startDate = yearMonth.atDay(1).atStartOfDay()
            val endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59)

            val income = transactionRepository.getSumByType(TransactionType.INCOME, startDate, endDate)
            val expense = transactionRepository.getSumByType(TransactionType.EXPENSE, startDate, endDate)

            _monthlyIncome.value = income
            _monthlyExpense.value = expense
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            transactionRepository.deleteAll()
            categoryRepository.deleteAll()
            _monthlyIncome.value = 0.0
            _monthlyExpense.value = 0.0
        }
    }
}
