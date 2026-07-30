package com.example.jianji.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.jianji.data.JianjiDatabase

/**
 * Factory for TransactionViewModel (fallback, Koin preferred).
 * @deprecated Use Koin DI instead.
 */
@Deprecated("Use Koin DI instead")
class TransactionViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            val db = JianjiDatabase.getDatabase(application)
            return TransactionViewModel(
                application,
                db.transactionRepository(),
                db.accountRepository(),
                db.recurringTransactionRepository(),
                db.tagRepository(),
                db
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// Extension helpers for JianjiDatabase
private fun JianjiDatabase.transactionRepository() =
    com.example.jianji.data.TransactionRepository(transactionDao())

private fun JianjiDatabase.accountRepository() =
    com.example.jianji.data.AccountRepository(accountDao())

private fun JianjiDatabase.recurringTransactionRepository() =
    com.example.jianji.data.RecurringTransactionRepository(recurringTransactionDao())

private fun JianjiDatabase.tagRepository() =
    com.example.jianji.data.TagRepository(tagDao())