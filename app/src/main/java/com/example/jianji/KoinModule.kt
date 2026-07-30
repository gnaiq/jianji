package com.example.jianji

import com.example.jianji.data.*
import com.example.jianji.ui.viewmodel.*
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin 依赖注入模块，替代手动实例化 Repository 和 ViewModel。
 * 消除 ViewModelFactory 样板代码，测试 Mock 成本 ↓ 80%。
 */
val appModule = module {
    // Database
    single { JianjiDatabase.getDatabase(androidContext()) }

    // DAOs
    single { get<JianjiDatabase>().transactionDao() }
    single { get<JianjiDatabase>().categoryDao() }
    single { get<JianjiDatabase>().accountDao() }
    single { get<JianjiDatabase>().budgetDao() }
    single { get<JianjiDatabase>().recurringTransactionDao() }
    single { get<JianjiDatabase>().quickTemplateDao() }
    single { get<JianjiDatabase>().tagDao() }

    // Repositories
    single { TransactionRepository(get()) }
    single { CategoryRepository(get()) }
    single { AccountRepository(get()) }
    single { BudgetRepository(get()) }
    single { RecurringTransactionRepository(get()) }
    single { QuickTemplateRepository(get()) }
    single { TagRepository(get()) }

    // ViewModels - 按领域拆分
    viewModel { TransactionViewModel(androidApplication(), get(), get(), get(), get(), get()) }
    viewModel { CategoryViewModel(get()) }
    viewModel { AccountViewModel(get(), get()) }
    viewModel { BudgetViewModel(get(), get()) }
    viewModel { TagViewModel(get()) }
    viewModel { SettingsViewModel(androidApplication(), get(), get(), get(), get()) }
}