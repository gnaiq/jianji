package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.jianji.data.*
import com.example.jianji.utils.AutoBackup
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * 设置与工具 ViewModel：模板管理、周期交易、备份、清除数据。
 */
class SettingsViewModel(
    application: Application,
    private val templateRepo: QuickTemplateRepository,
    private val recurringRepo: RecurringTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepo: CategoryRepository,
    private val accountRepo: AccountRepository,
    private val budgetRepo: BudgetRepository,
    private val tagRepo: TagRepository,
    private val database: JianjiDatabase
) : AndroidViewModel(application) {

    private val clearing = AtomicBoolean(false)

    val allTemplates: StateFlow<List<QuickTemplate>> = templateRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTransactions: StateFlow<List<RecurringTransaction>> = recurringRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -- Templates --
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

    fun deleteAllTemplates() {
        viewModelScope.launch { templateRepo.deleteAll() }
    }

    // -- Recurring --
    fun addRecurring(tx: RecurringTransaction) {
        viewModelScope.launch { recurringRepo.insert(tx) }
    }

    fun updateRecurring(tx: RecurringTransaction) {
        viewModelScope.launch { recurringRepo.update(tx) }
    }

    fun deleteRecurring(tx: RecurringTransaction) {
        viewModelScope.launch { recurringRepo.delete(tx) }
    }

    fun deleteAllRecurring() {
        viewModelScope.launch { recurringRepo.deleteAll() }
    }

    // -- Auto Backup --
    fun autoBackup() {
        viewModelScope.launch {
            try {
                AutoBackup.run(getApplication())
            } catch (e: Exception) {
                Timber.w(e, "autoBackup failed")
            }
        }
    }

    // -- Clear all data --
    /**
     * 清除全部业务数据并重置为默认分类/账户。
     *
     * 两个关键约束：
     *  1. 全部操作在**单一协程 + 单一 Room 事务**内顺序执行。历史实现由 UI 层并列调用
     *     8 个 ViewModel 方法（各自 viewModelScope.launch），Room 的查询线程池会并发调度它们，
     *     seedDefaults() 内的 getCount()/getAll() 与 deleteAll() 之间没有 happens-before 关系，
     *     WAL 模式下极易读到删除前的旧快照 → 守卫直接 return，清完数据后一条默认分类都不补种，
     *     用户将无法记任何一笔账。放进同一事务后，seedDefaults 能看到本事务内未提交的 DELETE。
     *  2. 标签表必须一并清空（历史实现遗漏），否则 transaction_tags 虽被 CASCADE 清理，
     *     标签本体仍全量残留。
     *
     * 破坏性操作前先落一份「操作前快照」备份，该文件不参与自动备份轮转，给误操作留后悔药。
     */
    fun clearAllData() {
        if (!clearing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try { performClear() }
            finally { clearing.set(false) }
        }
    }

    /**
     * 实际清除逻辑（单 Room 事务），抽离为 suspend 以便单测直接调用，
     * 避免依赖 viewModelScope 的调度，测试可确定性地等待完成。
     */
    internal suspend fun performClear() {
        AutoBackup.snapshotBeforeDestructive(getApplication(), "清除数据前")
        database.withTransaction {
            transactionRepository.deleteAll()
            categoryRepo.deleteAll()
            budgetRepo.deleteAll()
            templateRepo.deleteAll()
            recurringRepo.deleteAll()
            accountRepo.deleteAll()
            tagRepo.deleteAll()
            categoryRepo.seedDefaults()
            accountRepo.seedDefaults()
        }
    }
}