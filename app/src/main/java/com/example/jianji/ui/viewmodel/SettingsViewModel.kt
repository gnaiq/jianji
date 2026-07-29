package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import com.example.jianji.utils.AutoBackup
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 设置与工具 ViewModel：模板管理、周期交易、备份、清除数据。
 */
class SettingsViewModel(
    application: Application,
    private val templateRepo: QuickTemplateRepository,
    private val recurringRepo: RecurringTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val database: JianjiDatabase
) : AndroidViewModel(application) {

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
    fun clearAllData(
        categoryRepo: CategoryRepository,
        accountRepo: AccountRepository,
        budgetRepo: BudgetRepository
    ) {
        viewModelScope.launch {
            transactionRepository.deleteAll()
            categoryRepo.deleteAll()
            budgetRepo.deleteAll()
            templateRepo.deleteAll()
            recurringRepo.deleteAll()
            accountRepo.deleteAll()
            categoryRepo.seedDefaults()
            accountRepo.seedDefaults()
        }
    }
}