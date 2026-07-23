package com.example.jianji.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jianji.data.CategoryType
import com.example.jianji.data.Transaction
import com.example.jianji.ui.components.AddTransactionDialog
import com.example.jianji.ui.screens.AddCategoryDialog
import com.example.jianji.ui.screens.CategoryManagementScreen
import com.example.jianji.ui.screens.HomeScreen
import com.example.jianji.ui.screens.SettingsScreen
import com.example.jianji.ui.screens.StatisticsScreen
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.ui.viewmodel.TransactionViewModelFactory

@Composable
fun JianjiApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var addCategoryType by remember { mutableStateOf(CategoryType.EXPENSE) }

    val context = LocalContext.current
    val viewModel: TransactionViewModel = viewModel(
        factory = TransactionViewModelFactory(context)
    )

    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Statistics") },
                    label = { Text("统计") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Category, contentDescription = "Category") },
                    label = { Text("分类") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("设置") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTransaction = null
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    transactions = transactions,
                    categories = categories,
                    monthlyIncome = monthlyIncome,
                    monthlyExpense = monthlyExpense,
                    onEdit = { transaction ->
                        editingTransaction = transaction
                        showAddDialog = true
                    },
                    onDelete = { transaction -> viewModel.deleteTransaction(transaction) }
                )
                1 -> StatisticsScreen(
                    transactions = transactions,
                    categories = categories
                )
                2 -> CategoryManagementScreen(
                    categories = categories,
                    onAddCategory = { name, icon, type -> viewModel.addCategory(name, icon, type) },
                    onDeleteCategory = { viewModel.deleteCategory(it) },
                    onEditCategory = { }
                )
                3 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

    // 添加 / 编辑交易
    if (showAddDialog) {
        AddTransactionDialog(
            categories = categories,
            editingTransaction = editingTransaction,
            onDismiss = {
                showAddDialog = false
                editingTransaction = null
            },
            onRequestAddCategory = { type ->
                addCategoryType = type
                showAddCategoryDialog = true
            },
            onConfirm = { categoryId, amount, type, description, date ->
                val editing = editingTransaction
                if (editing != null) {
                    viewModel.updateTransaction(
                        editing.copy(
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
            }
        )
    }

    // 在添加交易时快速新建分类
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            categoryType = addCategoryType,
            onAdd = { name, icon ->
                viewModel.addCategory(name, icon, addCategoryType)
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }
}
