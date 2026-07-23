package com.example.jianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jianji.data.*
import com.example.jianji.ui.components.AddTransactionDialog
import com.example.jianji.ui.components.CategoryFormDialog
import com.example.jianji.ui.screens.*
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.ui.viewmodel.TransactionViewModelFactory
import com.example.jianji.data.Transaction as AppTransaction

enum class Tab(val label: String) {
    HOME("首页"),
    STATISTICS("统计"),
    CATEGORIES("分类管理"),
    SETTINGS("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JianjiApp() {
    val context = LocalContext.current
    val viewModel: TransactionViewModel = viewModel(
        factory = TransactionViewModelFactory(context.applicationContext as android.app.Application)
    )
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()
    val dailyExpense by viewModel.dailyExpense.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val allTemplates by viewModel.allTemplates.collectAsState()
    val allRecurring by viewModel.recurringTransactions.collectAsState()

    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialogTab by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<AppTransaction?>(null) }
    var showAddCategoryQuick by remember { mutableStateOf(false) }
    var addCategoryQuickType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var categoryTabType by remember { mutableStateOf(CategoryType.EXPENSE) }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (selectedTab != Tab.SETTINGS && !isSearching) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == Tab.CATEGORIES) {
                            showAddCategoryDialogTab = true
                        } else {
                            editingTransaction = null
                            showAddDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Tab bar
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            if (tab != Tab.HOME) { isSearching = false; searchQuery = "" }
                        },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                Tab.HOME -> HomeScreen(
                    transactions = transactions,
                    categories = categories,
                    monthlyIncome = monthlyIncome,
                    monthlyExpense = monthlyExpense,
                    dailyExpense = dailyExpense,
                    accounts = allAccounts,
                    templates = allTemplates,
                    recurringTransactions = allRecurring,
                    searchQuery = searchQuery,
                    isSearching = isSearching,
                    onSearchQueryChange = { searchQuery = it },
                    onToggleSearch = { isSearching = !isSearching },
                    onTransactionClick = { editingTransaction = it },
                    onDeleteTransaction = { viewModel.deleteTransaction(it) },
                    onUseTemplate = { template ->
                        viewModel.useTemplate(template.id)
                        viewModel.addTransaction(
                            categoryId = template.categoryId,
                            amount = template.amount,
                            type = template.type,
                            description = template.description,
                            date = java.time.LocalDateTime.now(),
                            accountId = template.accountId
                        )
                    },
                    onProcessRecurring = { viewModel.processRecurringDue() }
                )
                Tab.STATISTICS -> StatisticsScreen(
                    transactions = transactions,
                    categories = categories
                )
                Tab.CATEGORIES -> CategoryManagementScreen(
                    categories = categories,
                    onAddCategory = { name, icon, type ->
                        viewModel.addCategory(name, icon, type)
                    },
                    onDeleteCategory = { viewModel.deleteCategory(it) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
                    showAddCategoryDialog = showAddCategoryDialogTab,
                    onDismissAddDialog = { showAddCategoryDialogTab = false },
                    onTypeChanged = { categoryTabType = it }
                )
                Tab.SETTINGS -> SettingsScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = allAccounts,
                    templates = allTemplates,
                    recurringTransactions = allRecurring,
                    viewModel = viewModel,
                    onDataCleared = { viewModel.clearAllData() }
                )
            }
        }

        // Transaction dialog
        if (showAddDialog || editingTransaction != null) {
            AddTransactionDialog(
                categories = categories,
                editingTransaction = editingTransaction,
                templates = allTemplates,
                accounts = allAccounts,
                onDismiss = {
                    showAddDialog = false
                    editingTransaction = null
                },
                onConfirm = { categoryId, amount, type, description, date, accountId ->
                    if (editingTransaction != null) {
                        viewModel.updateTransaction(
                            editingTransaction!!.copy(
                                categoryId = categoryId,
                                amount = amount,
                                type = type,
                                description = description,
                                date = date,
                                accountId = accountId
                            )
                        )
                    } else {
                        viewModel.addTransaction(categoryId, amount, type, description, date, accountId)
                    }
                    showAddDialog = false
                    editingTransaction = null
                },
                onRequestAddCategory = { type ->
                    addCategoryQuickType = type
                    showAddCategoryQuick = true
                }
            )
        }

        if (showAddCategoryQuick) {
                CategoryFormDialog(
                title = "添加分类",
                categoryType = addCategoryQuickType,
                onConfirm = { name, icon ->
                    val ct = if (addCategoryQuickType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                    viewModel.addCategory(name, icon, ct)
                    showAddCategoryQuick = false
                },
                onDismiss = { showAddCategoryQuick = false }
            )
        }
    }
}
