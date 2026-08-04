package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.jianji.data.*
import com.example.jianji.utils.DateUtils
import com.example.jianji.utils.computeRecurringNextRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    // 修复 B3-4：以 Long 分累加资金流（见 computeAccountBalancesCents），仅在最终输出时除以 100，
    // 避免逐笔 `amountCents / 100.0` 转 Double 累加导致的浮点精度丢失。
    val accountBalances: StateFlow<Map<Long, Double>> = transactions
        .map { txs -> computeAccountBalancesCents(txs).mapValues { (_, v) -> v / 100.0 } }
        // ⚠️ 必须保持 Eagerly，不可改回 WhileSubscribed：
        // SettingsScreen 与 JianjiApp 的账户弹窗是以 `accountBalances.value` 快照方式读取的
        // （读 .value 不构成订阅）。若改回 WhileSubscribed，上游在无订阅者时不会启动收集，
        // .value 将恒为初始 emptyMap()，账户余额会再次全部显示为 ¥0.00。
        // 此处 Eagerly 同时兼任上游 transactions(WhileSubscribed) 的常驻订阅者。
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
    // D2-2/D2-3：getDue 必须在事务内执行，且用 Mutex 防重入——
    // 否则并发两次调用各自先 getDue 再排队事务，会读到相同 due 导致重复记账。
    private val recurringMutex = Mutex()

    fun processRecurringDue() {
        viewModelScope.launch {
            recurringMutex.withLock {
                database.withTransaction {
                    val now = LocalDateTime.now()
                    val due = recurringRepo.getDue(now)
                    val defaultAcc = accountRepo.getDefault()?.id
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
                                    amountCents = rtx.amountCents,
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
    }

    private fun nextRunAfter(prev: LocalDateTime, rtx: RecurringTransaction): LocalDateTime {
        // 修复 D2-1：catch-up 推进必须与 computeRecurringNextRun（预览/保存）的语义一致，
        // 避免 WEEKLY 用简单 plusWeeks 跳过 dayOfWeek 导致的重复/漏记。
        // 以 prev 之后第一个符合频率规则的日期为基准，复用统一的"从 now 推算"算法。
        val base = prev.plusSeconds(1)
        return computeRecurringNextRun(
            freq = rtx.frequency,
            dayOfMonth = rtx.dayOfMonth,
            interval = rtx.interval,
            dayOfWeek = rtx.dayOfWeek,
            monthOfYear = rtx.monthOfYear,
            now = base
        )
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

/**
 * 修复 B3-4：账户余额以 Long 分累加（纯函数，便于单测）。
 * 输入交易列表，返回 accountId -> 余额（单位：分）。转账的起账户减、止账户加。
 */
internal fun computeAccountBalancesCents(txs: List<Transaction>): Map<Long, Long> {
    val cents = mutableMapOf<Long, Long>()
    fun add(accId: Long?, deltaCents: Long) {
        if (accId == null) return
        cents[accId] = (cents[accId] ?: 0L) + deltaCents
    }
    for (t in txs) {
        when (t.type) {
            TransactionType.INCOME -> add(t.accountId, t.amountCents)
            TransactionType.EXPENSE -> add(t.accountId, -t.amountCents)
            TransactionType.TRANSFER -> {
                add(t.accountId, -t.amountCents)
                add(t.toAccountId, t.amountCents)
            }
        }
    }
    return cents
}