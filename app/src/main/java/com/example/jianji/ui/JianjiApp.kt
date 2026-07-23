package com.example.jianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jianji.data.*
import com.example.jianji.ui.components.AddTransactionDialog
import com.example.jianji.ui.screens.*
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.data.Transaction as AppTransaction

enum class Tab(val label: String) {
    HOME("首页"),
    STATISTICS("统计"),
    CATEGORIES("分类管理")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JianjiApp(
    viewModel: TransactionViewModel = viewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()
    val dailyExpense by viewModel.dailyExpense.collectAsState()

    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialogTab by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<AppTransaction?>(null) }

    // 在Transaction对话框内触发的添加分类（非标签页FAB触发）
    var showAddCategoryQuick by remember { mutableStateOf(false) }
    var addCategoryQuickType by remember { mutableStateOf(CategoryType.EXPENSE) }

    // 分类管理页的当前类型
    var categoryTabType by remember { mutableStateOf(CategoryType.EXPENSE) }

    Scaffold(
        floatingActionButton = {
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
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Tab bar
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
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
                    onTransactionClick = { editingTransaction = it },
                    onDeleteTransaction = { viewModel.deleteTransaction(it) }
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
            }
        }

        // Transaction 新增/编辑对话框
        if (showAddDialog || editingTransaction != null) {
            AddTransactionDialog(
                categories = categories,
                editingTransaction = editingTransaction,
                onDismiss = {
                    showAddDialog = false
                    editingTransaction = null
                },
                onSave = { categoryId, amount, type, description, date ->
                    if (editingTransaction != null) {
                        viewModel.updateTransaction(
                            editingTransaction!!.copy(
                                categoryId = categoryId,
                                amount = amount,
                                type = type,
                                description = description,
                                date = date
                            )
                        )
                    } else {
                        viewModel.addTransaction(categoryId, amount, type, description, date)
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

        // Transaction 对话框内触发的快速添加分类
        if (showAddCategoryQuick) {
            CategoryFormDialog(
                title = "添加分类",
                categoryType = addCategoryQuickType,
                onConfirm = { name, icon ->
                    viewModel.addCategory(name, icon, addCategoryQuickType)
                    showAddCategoryQuick = false
                },
                onDismiss = { showAddCategoryQuick = false }
            )
        }
    }
}
