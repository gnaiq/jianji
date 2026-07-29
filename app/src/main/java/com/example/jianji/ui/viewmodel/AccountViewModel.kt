package com.example.jianji.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jianji.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 账户管理 ViewModel：账户 CRUD、默认账户设置。
 */
class AccountViewModel(
    private val accountRepo: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val allAccounts: StateFlow<List<Account>> = accountRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAccount(name: String, icon: String = "💳") {
        viewModelScope.launch { accountRepo.insert(Account(name = name, icon = icon)) }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch { accountRepo.update(account) }
    }

    fun deleteAccountCascade(account: Account) {
        viewModelScope.launch {
            transactionRepository.reassignAccountToNull(account.id)
            accountRepo.delete(account)
        }
    }

    fun setDefaultAccount(id: Long) {
        viewModelScope.launch { accountRepo.setDefault(id) }
    }

    fun seedDefaults() {
        viewModelScope.launch { accountRepo.seedDefaults() }
    }

    fun deleteAll() {
        viewModelScope.launch { accountRepo.deleteAll() }
    }
}