package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class TransactionViewModel(application: Application) : AndroidViewModel(application) {
    private val database = JianjiDatabase.getDatabase(application)
    val transactionRepository = TransactionRepository(database.transactionDao())
    val categoryRepository = CategoryRepository(database.categoryDao())
    private val accountRepo = AccountRepository(database.accountDao())
    private val budgetRepo = BudgetRepository(database.budgetDao())
    private val recurringRepo = RecurringTransactionRepository(database.recurringTransactionDao())
    private val templateRepo = QuickTemplateRepository(database.quickTemplateDao())

    // === 保持兼容的 Public StateFlows ===
    val transactions: StateFlow<List<Transaction>> = transactionRepository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<Account>> = flow { emit(accountRepo.getAll()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<Category>> = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<Category>> = categoryRepository.getCategoriesByType(TransactionType.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTemplates: StateFlow<List<QuickTemplate>> = flow { emit(templateRepo.getAll()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTransactions: StateFlow<List<RecurringTransaction>> = flow { emit(recurringRepo.getAll()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当月收支
    private val thisMonthStart = YearMonth.now().atDay(1).atStartOfDay()
    private val nextMonthStart = YearMonth.now().plusMonths(1).atDay(1).atStartOfDay()

    val monthlyIncome: StateFlow<Double> = transactionRepository.getTransactionsByDateRange(thisMonthStart, nextMonthStart)
        .map { txs -> txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense: StateFlow<Double> = transactionRepository.getTransactionsByDateRange(thisMonthStart, nextMonthStart)
        .map { txs -> txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 今日支出
    private val todayStart = LocalDate.now().atStartOfDay()
    private val tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay()

    val dailyExpense: StateFlow<Double> = transactionRepository.getTransactionsByDateRange(todayStart, tomorrowStart)
        .map { txs -> txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // -- Transaction CRUD --
    fun addTransaction(
        categoryId: Long,
        amount: Double,
        type: TransactionType,
        description: String,
        date: LocalDateTime,
        accountId: Long? = null
    ) {
        viewModelScope.launch {
            transactionRepository.insertTransaction(
                Transaction(
                    categoryId = categoryId,
                    amount = amount,
                    type = type,
                    description = description,
                    date = date,
                    accountId = accountId ?: accountRepo.getDefault()?.id
                )
            )
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction.copy(updatedAt = LocalDateTime.now()))
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { transactionRepository.deleteTransaction(transaction) }
    }

    // -- Category CRUD --
    fun addCategory(name: String, icon: String = "📁", type: CategoryType) {
        viewModelScope.launch {
            val maxOrder = categoryRepository.getMaxSortOrder()
            categoryRepository.insertCategory(Category(name = name, type = type, icon = icon, sortOrder = maxOrder + 1))
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { categoryRepository.updateCategory(category) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { categoryRepository.deleteCategory(category) }
    }

    // -- Account CRUD --
    fun addAccount(name: String, icon: String = "💳") {
        viewModelScope.launch { accountRepo.insert(Account(name = name, icon = icon)) }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch { accountRepo.update(account) }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { accountRepo.delete(account) }
    }

    fun setDefaultAccount(id: Long) {
        viewModelScope.launch { accountRepo.setDefault(id) }
    }

    // -- Budget --
    fun setBudget(budget: Budget) {
        viewModelScope.launch { budgetRepo.setBudget(budget) }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { budgetRepo.delete(budget) }
    }

    suspend fun getBudgetProgress(year: Int, month: Int, budget: Budget?, categoryId: Long? = null): BudgetProgress {
        return transactionRepository.getBudgetProgress(year, month, budget, categoryId)
    }

    // -- Quick Templates --
    fun addTemplate(template: QuickTemplate) {
        viewModelScope.launch { templateRepo.insert(template) }
    }

    fun updateTemplate(template: QuickTemplate) {
        viewModelScope.launch { templateRepo.update(template) }
    }

    fun deleteTemplate(template: QuickTemplate) {
        viewModelScope.launch { templateRepo.delete(template) }
    }

    fun useTemplate(templateId: Long) {
        viewModelScope.launch { templateRepo.incrementUseCount(templateId) }
    }

    // -- Recurring Transactions --
    fun addRecurring(tx: RecurringTransaction) {
        viewModelScope.launch { recurringRepo.insert(tx) }
    }

    fun updateRecurring(tx: RecurringTransaction) {
        viewModelScope.launch { recurringRepo.update(tx) }
    }

    fun deleteRecurring(tx: RecurringTransaction) {
        viewModelScope.launch { recurringRepo.delete(tx) }
    }

    fun processRecurringDue() {
        viewModelScope.launch {
            val due = recurringRepo.getDue(LocalDateTime.now())
            val defaultAcc = accountRepo.getDefault()?.id
            for (rtx in due) {
                transactionRepository.insertTransaction(
                    Transaction(
                        categoryId = rtx.categoryId,
                        amount = rtx.amount,
                        type = rtx.type,
                        description = rtx.description,
                        date = LocalDateTime.now(),
                        accountId = rtx.accountId ?: defaultAcc
                    )
                )
                val next = rtx.nextRunDate.let { cur ->
                    when (rtx.frequency) {
                        RecurringFrequency.DAILY -> cur.plusDays(rtx.interval.toLong())
                        RecurringFrequency.WEEKLY -> cur.plusWeeks(rtx.interval.toLong())
                        RecurringFrequency.MONTHLY -> cur.plusMonths(rtx.interval.toLong())
                        RecurringFrequency.YEARLY -> cur.plusYears(rtx.interval.toLong())
                    }
                }
                recurringRepo.update(rtx.copy(nextRunDate = next))
            }
        }
    }

    // -- Clear all data --
    fun clearAllData() {
        viewModelScope.launch {
            transactionRepository.deleteAll()
            categoryRepository.deleteAll()
            budgetRepo.deleteAll()
            templateRepo.deleteAll()
            recurringRepo.deleteAll()
            categoryRepository.seedDefaults()
            accountRepo.seedDefaults()
        }
    }

    // -- Snapshots for export --
    suspend fun getAllTransactionsSnapshot(): List<Transaction> = transactionRepository.getAllSnapshot()
    suspend fun getTransactionsByDateSnapshot(start: LocalDateTime, end: LocalDateTime): List<Transaction> =
        transactionRepository.getByDateRangeSnapshot(start, end)

    // -- Seed initial data --
    init {
        viewModelScope.launch {
            categoryRepository.seedDefaults()
            accountRepo.seedDefaults()
            recurringRepo.getDue(LocalDateTime.now()).let { due ->
                if (due.isNotEmpty()) processRecurringDue()
            }
        }
    }
}
