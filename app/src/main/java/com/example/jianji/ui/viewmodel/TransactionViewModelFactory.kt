package com.example.jianji.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.jianji.data.CategoryRepository
import com.example.jianji.data.JianjiDatabase
import com.example.jianji.data.TransactionRepository

class TransactionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            val database = JianjiDatabase.getDatabase(context)
            val transactionRepository = TransactionRepository(database.transactionDao())
            val categoryRepository = CategoryRepository(database.categoryDao())
            return TransactionViewModel(transactionRepository, categoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
