package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import com.example.jianji.utils.AutoBackup
import com.example.jianji.utils.BackupStorage
import androidx.room.withTransaction
import com.example.jianji.utils.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
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

    // 当月预算：随月份切换动态重查（P0-2：原 YearMonth.now() 在 VM 构造时固化，进程跨月驻留会读旧月预算）
    val monthlyBudget: StateFlow<Double> = currentMonthFlow()
        .flatMapLatest { ym ->
            budgetRepo.observeTotalBudget(ym.year, ym.monthValue).map { it?.amount ?: 0.0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 仅在月份边界重新发射当前 YearMonth，避免每次交易都重查预算
    private fun currentMonthFlow(): Flow<YearMonth> = flow {
        while (true) {
            val (start, end) = DateUtils.currentMonthRange()
            emit(YearMonth.from(start.toLocalDate()))
            val waitMs = Duration.between(LocalDateTime.now(), end).toMillis()
            delay(if (waitMs > 0) waitMs else 1000L)
        }
    }

    // 各账户实时余额：由交易汇总，而非依赖存储字段（6.1 P0 修复「死数据」）
    // 收入 +amount；支出 -amount；转账 源账户 -amount、目标账户 +amount
    val accountBalances: StateFlow<Map<Long, Double>> = transactions
        .map { txs ->
            val map = mutableMapOf<Long, Double>()
            fun add(accId: Long?, delta: Double) {
                if (accId == null) return
                map[accId] = (map[accId] ?: 0.0) + delta
            }
            for (t in txs) {
                when (t.type) {
                    TransactionType.INCOME -> add(t.accountId, t.amount)
                    TransactionType.EXPENSE -> add(t.accountId, -t.amount)
                    TransactionType.TRANSFER -> {
                        add(t.accountId, -t.amount)
                        add(t.toAccountId, t.amount)
                    }
                }
            }
            map
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // -- Transaction CRUD --
    fun addTransaction(
        categoryId: Long,
        amount: Double,
        type: TransactionType,
        description: String,
        date: LocalDateTime,
        accountId: Long? = null,
        toAccountId: Long? = null
    ) {
        viewModelScope.launch {
            transactionRepository.insertTransaction(
                Transaction(
                    categoryId = categoryId,
                    amount = amount,
                    type = type,
                    description = description,
                    date = date,
                    accountId = accountId ?: accountRepo.getDefault()?.id,
                    toAccountId = toAccountId
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
            // P0-3：插入补账交易与推进 nextRunDate 必须原子提交，
            // 避免「插入完成、推进前」进程被杀导致下次启动重复入账
            database.withTransaction {
                for (rtx in due) {
                    // 生成本次及错过的所有周期（补账），交易日期 = 各自执行日
                    val occurrences = mutableListOf<LocalDateTime>()
                    var cur = rtx.nextRunDate
                    var guard = 0
                    while (cur <= now && guard < 1000) {
                        occurrences.add(cur)
                        cur = nextRunAfter(cur, rtx)
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
                    while (next <= now) next = nextRunAfter(next, rtx)
                    if (next != rtx.nextRunDate) recurringRepo.update(rtx.copy(nextRunDate = next))
                }
            }
        }
    }

    // 与 computeRecurringNextRun 同源：基于上次发生日锚定下一次，保证创建预览与执行推进一致（6.1 P1 双轨不一致修复）
    private fun nextRunAfter(prev: LocalDateTime, rtx: RecurringTransaction): LocalDateTime {
        val iv = maxOf(1, rtx.interval)
        val dom = rtx.dayOfMonth.coerceIn(1, 31)
        return when (rtx.frequency) {
            RecurringFrequency.DAILY -> prev.toLocalDate().plusDays(iv.toLong()).atStartOfDay()
            RecurringFrequency.WEEKLY -> prev.plusWeeks(iv.toLong()) // 保持星期一致
            RecurringFrequency.MONTHLY -> {
                val d = prev.toLocalDate().plusMonths(iv.toLong())
                val day = if (dom > d.lengthOfMonth()) d.lengthOfMonth() else dom
                d.withDayOfMonth(day).atStartOfDay()
            }
            RecurringFrequency.YEARLY -> {
                val d = prev.toLocalDate().plusYears(iv.toLong())
                val m = rtx.monthOfYear.coerceIn(1, 12)
                val maxDom = java.time.YearMonth.of(d.year, m).lengthOfMonth()
                val day = if (dom > maxDom) maxDom else dom
                java.time.LocalDate.of(d.year, m, day).atStartOfDay()
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