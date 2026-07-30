package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.jianji.data.*
import com.example.jianji.utils.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * 交易核心 ViewModel：交易 CRUD、回收站、当月/当日汇总。
 * 按领域拆分后，仅保留交易相关职责，单文件从 410 行降至 ~150 行。
 */
class TransactionViewModel(
    application: Application,
    private val transactionRepository: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val recurringRepo: RecurringTransactionRepository,
    private val tagRepo: TagRepository,
    private val database: JianjiDatabase
) : AndroidViewModel(application) {

    // === 交易列表 ===
    val transactions: StateFlow<List<Transaction>> = transactionRepository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 回收站
    val deletedTransactions: StateFlow<List<Transaction>> = transactionRepository.getDeletedTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当月收支（SQL SUM + date 索引下推，月份边界自动切换）
    val monthlyIncome: StateFlow<Double> = currentMonthFlow()
        .flatMapLatest { ym ->
            transactionRepository.observeSumByType(
                TransactionType.INCOME, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay()
            )
        }
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyExpense: StateFlow<Double> = currentMonthFlow()
        .flatMapLatest { ym ->
            transactionRepository.observeSumByType(
                TransactionType.EXPENSE, ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay()
            )
        }
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 今日支出
    val dailyExpense: StateFlow<Double> = currentDayFlow()
        .flatMapLatest { day ->
            transactionRepository.observeSumByType(
                TransactionType.EXPENSE, day.atStartOfDay(), day.plusDays(1).atStartOfDay()
            )
        }
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 各账户实时余额（由交易汇总计算）
    val accountBalances: StateFlow<Map<Long, Double>> = transactions
        .map { txs ->
            val map = mutableMapOf<Long, Double>()
            fun add(accId: Long?, delta: Double) {
                if (accId == null) return
                map[accId] = (map[accId] ?: 0.0) + delta
            }
            for (t in txs) {
                when (t.type) {
                    TransactionType.INCOME -> add(t.accountId, t.amountCents / 100.0)
                    TransactionType.EXPENSE -> add(t.accountId, -t.amountCents / 100.0)
                    TransactionType.TRANSFER -> {
                        add(t.accountId, -t.amountCents / 100.0)
                        add(t.toAccountId, t.amountCents / 100.0)
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
        toAccountId: Long? = null,
        tagIds: List<Long> = emptyList()
    ) {
        viewModelScope.launch {
            // 交易与标签关联在同一事务内写入，避免中途失败产生「有交易无标签」的脏数据
            database.withTransaction {
                val insertedId = transactionRepository.insertTransaction(
                    Transaction(
                        categoryId = categoryId,
                        amountCents = Math.round(amount * 100),
                        type = type,
                        description = description,
                        date = date,
                        accountId = accountId ?: accountRepo.getDefault()?.id,
                        toAccountId = toAccountId
                    )
                )
                if (tagIds.isNotEmpty()) tagRepo.setTransactionTags(insertedId, tagIds)
            }
        }
    }

    // tagIds 必填：默认值会让漏传的调用方静默清空该交易的全部标签
    fun updateTransaction(transaction: Transaction, tagIds: List<Long>) {
        viewModelScope.launch {
            database.withTransaction {
                transactionRepository.updateTransaction(transaction.copy(updatedAt = LocalDateTime.now()))
                // 全量覆盖：先清后插，支持「取消勾选全部标签」的清空语义
                tagRepo.setTransactionTags(transaction.id, tagIds)
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { transactionRepository.deleteTransaction(transaction) }
    }

    // -- 回收站 --
    fun softDelete(transaction: Transaction) {
        viewModelScope.launch { transactionRepository.softDelete(transaction) }
    }

    fun restoreTransaction(id: Long) {
        viewModelScope.launch { transactionRepository.restoreTransaction(id) }
    }

    fun purgeDeleted() {
        viewModelScope.launch { transactionRepository.purgeDeleted() }
    }

    fun getTransactionsByTagIds(ids: Set<Long>): Flow<List<Transaction>> =
        transactionRepository.getTransactionsByTagIds(ids.toList())

    // -- Recurring Processing --
    fun processRecurringDue() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val due = recurringRepo.getDue(now)
            val defaultAcc = accountRepo.getDefault()?.id
            database.withTransaction {
                for (rtx in due) {
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
                                amountCents = Math.round(rtx.amount * 100),
                                type = rtx.type,
                                description = rtx.description,
                                date = date,
                                accountId = rtx.accountId ?: defaultAcc
                            )
                        )
                    }
                    var next = rtx.nextRunDate
                    while (next <= now) next = nextRunAfter(next, rtx)
                    if (next != rtx.nextRunDate) recurringRepo.update(rtx.copy(nextRunDate = next))
                }
            }
        }
    }

    private fun nextRunAfter(prev: LocalDateTime, rtx: RecurringTransaction): LocalDateTime {
        val iv = maxOf(1, rtx.interval)
        val dom = rtx.dayOfMonth.coerceIn(1, 31)
        return when (rtx.frequency) {
            RecurringFrequency.DAILY -> prev.toLocalDate().plusDays(iv.toLong()).atStartOfDay()
            RecurringFrequency.WEEKLY -> prev.plusWeeks(iv.toLong())
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

    // -- Snapshots for export --
    suspend fun getAllTransactionsSnapshot(): List<Transaction> = transactionRepository.getAllSnapshot()
    suspend fun getTransactionsByDateSnapshot(start: LocalDateTime, end: LocalDateTime): List<Transaction> =
        transactionRepository.getByDateRangeSnapshot(start, end)

    fun deleteAll() {
        viewModelScope.launch { transactionRepository.deleteAll() }
    }

    fun reassignAccountToNull(accountId: Long) {
        viewModelScope.launch { transactionRepository.reassignAccountToNull(accountId) }
    }

    // -- 时间边界 Flow --
    private fun currentMonthFlow(): Flow<YearMonth> = flow {
        while (true) {
            val (start, end) = DateUtils.currentMonthRange()
            emit(YearMonth.from(start.toLocalDate()))
            val waitMs = Duration.between(LocalDateTime.now(), end).toMillis()
            delay(if (waitMs > 0) waitMs else 1000L)
        }
    }

    private fun currentDayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val (start, end) = DateUtils.todayRange()
            emit(start.toLocalDate())
            val waitMs = Duration.between(LocalDateTime.now(), end).toMillis()
            delay(if (waitMs > 0) waitMs else 1000L)
        }
    }
}