package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import com.example.jianji.utils.AutoBackup
import com.example.jianji.utils.BackupStorage
import com.example.jianji.utils.DateUtils
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

    val allAccounts: StateFlow<List<Account>> = accountRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<Category>> = categoryRepository.getCategoriesByType(TransactionType.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<Category>> = categoryRepository.getCategoriesByType(TransactionType.INCOME)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTemplates: StateFlow<List<QuickTemplate>> = templateRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTransactions: StateFlow<List<RecurringTransaction>> = recurringRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当月收支（每次发射重新计算当月区间，跨月后自动刷新；统一日期源）
    val monthlyIncome: StateFlow<Double> = transactions
        .map { txs ->
            val (s, e) = DateUtils.currentMonthRange()
            txs.filter { it.date >= s && it.date < e && it.type == TransactionType.INCOME }.sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense: StateFlow<Double> = transactions
        .map { txs ->
            val (s, e) = DateUtils.currentMonthRange()
            txs.filter { it.date >= s && it.date < e && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 今日支出（每次发射重新计算今日区间）
    val dailyExpense: StateFlow<Double> = transactions
        .map { txs ->
            val (s, e) = DateUtils.todayRange()
            txs.filter { it.date >= s && it.date < e && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 当月预算（设置页保存后由 Room Flow 自动刷新）
    val monthlyBudget: StateFlow<Double> = budgetRepo.observeTotalBudget(
        YearMonth.now().year, YearMonth.now().monthValue
    ).map { it?.amount ?: 0.0 }
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
            // 删除大类时级联删除其子分类（各自交易由外键 CASCADE 一并删除），
            // 避免子分类 parentId 悬空后在两级 UI 中不可见却仍残留在数据库
            if (category.parentId == 0L) {
                categoryRepository.getSiblings(category.type, category.id).forEach {
                    categoryRepository.deleteCategory(it)
                }
            }
            categoryRepository.deleteCategory(category)
        }
    }

    // -- Account CRUD --
    fun addAccount(name: String, icon: String = "💳") {
        viewModelScope.launch { accountRepo.insert(Account(name = name, icon = icon)) }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch { accountRepo.update(account) }
    }

    // 删除账户前先解绑其交易（accountId 置空），防止悬空引用（P1-4 账户删除治理）
    fun deleteAccountCascade(account: Account) {
        viewModelScope.launch {
            transactionRepository.reassignAccountToNull(account.id)
            accountRepo.delete(account)
        }
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
            val now = LocalDateTime.now()
            val due = recurringRepo.getDue(now)
            val defaultAcc = accountRepo.getDefault()?.id
            for (rtx in due) {
                // 生成本次及错过的所有周期（补账），交易日期 = 各自执行日
                val occurrences = mutableListOf<LocalDateTime>()
                var cur = rtx.nextRunDate
                var guard = 0
                while (cur <= now && guard < 1000) {
                    occurrences.add(cur)
                    cur = advance(cur, rtx)
                    guard++
                }
                occurrences.forEach { date ->
                    transactionRepository.insertTransaction(
                        Transaction(
                            categoryId = rtx.categoryId,
                            amount = rtx.amount,
                            type = rtx.type,
                            description = rtx.description,
                            date = date,
                            accountId = rtx.accountId ?: defaultAcc
                        )
                    )
                }
                // 将下次执行日推进到未来，避免重复生成
                var next = rtx.nextRunDate
                while (next <= now) next = advance(next, rtx)
                if (next != rtx.nextRunDate) recurringRepo.update(rtx.copy(nextRunDate = next))
            }
        }
    }

    private fun advance(date: LocalDateTime, rtx: RecurringTransaction): LocalDateTime {
        return when (rtx.frequency) {
            RecurringFrequency.DAILY -> date.plusDays(rtx.interval.toLong())
            RecurringFrequency.WEEKLY -> date.plusWeeks(rtx.interval.toLong())
            RecurringFrequency.MONTHLY -> date.plusMonths(rtx.interval.toLong())
            RecurringFrequency.YEARLY -> date.plusYears(rtx.interval.toLong())
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
            accountRepo.deleteAll() // 修复：此前漏删账户，自定义账户在"清除所有数据"后残留
            categoryRepository.seedDefaults()
            accountRepo.seedDefaults()
        }
    }

    // -- Snapshots for export --
    suspend fun getAllTransactionsSnapshot(): List<Transaction> = transactionRepository.getAllSnapshot()
    suspend fun getTransactionsByDateSnapshot(start: LocalDateTime, end: LocalDateTime): List<Transaction> =
        transactionRepository.getByDateRangeSnapshot(start, end)

    // -- 自动备份：写入共享目录，卸载后仍可恢复 --
    fun autoBackup() {
        viewModelScope.launch {
            try {
                AutoBackup.run(getApplication())
            } catch (_: Exception) { }
        }
    }

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