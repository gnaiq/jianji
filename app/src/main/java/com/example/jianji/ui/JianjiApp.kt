package com.example.jianji.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jianji.data.*
import com.example.jianji.utils.BackupScheduler
import com.example.jianji.widget.JianjiWidget
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.jianji.ui.components.AddTransactionDialog
import com.example.jianji.ui.components.CategoryFormDialog
import com.example.jianji.ui.components.TagFormDialog
import com.example.jianji.ui.screens.*
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.ui.viewmodel.TransactionViewModelFactory
import com.example.jianji.data.Transaction as AppTransaction
import java.time.LocalDate

// §4 五页签迁移底部 NavigationBar：图标 + 短标签，「分类管理」更名「分类」
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    STATISTICS("统计", Icons.Filled.BarChart),
    CATEGORIES("分类", Icons.Filled.Category),
    HISTORY("历史", Icons.Filled.History),
    SETTINGS("设置", Icons.Filled.Settings),
    CALENDAR("日历", Icons.Filled.DateRange),
    RECYCLE("回收站", Icons.Filled.DeleteSweep),
    TAGS("标签", Icons.Filled.Label)
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun JianjiApp(
    darkMode: Int = 0,
    onDarkModeChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: TransactionViewModel = viewModel(
        factory = TransactionViewModelFactory(context.applicationContext as android.app.Application)
    )
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val monthlyIncome by viewModel.monthlyIncome.collectAsState()
    val monthlyExpense by viewModel.monthlyExpense.collectAsState()
    val dailyExpense by viewModel.dailyExpense.collectAsState()
    val monthlyBudget by viewModel.monthlyBudget.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val allTemplates by viewModel.allTemplates.collectAsState()
    val allRecurring by viewModel.recurringTransactions.collectAsState()

    // 自动备份：数据变化时写入共享目录（卸载后仍可恢复）
    LaunchedEffect(Unit) {
        BackupScheduler.ensureScheduled(context)
    }
        // §1 P0 即时备份节流：跳过冷启动初始发射 + debounce 5s，
        // 连续记 N 笔账仅在停止操作 5 秒后合并为一次全量落盘（原实现每次发射都写 MediaStore）
        LaunchedEffect(Unit) {
            snapshotFlow { transactions }
                .drop(1)
                .debounce(5_000)
                .collect {
                    if (BackupScheduler.isImmediateBackupEnabled(context)) {
                        viewModel.autoBackup()
                    }
                }
        }
        // 数据变化时刷新桌面小组件（P1-6）
        LaunchedEffect(transactions) {
            try {
                val manager = GlanceAppWidgetManager(context)
                manager.getGlanceIds(JianjiWidget::class.java).forEach { id: GlanceId ->
                    JianjiWidget().update(context, id)
                }
            } catch (_: Exception) { }
        }

    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialogTab by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<AppTransaction?>(null) }
    var showAddCategoryQuick by remember { mutableStateOf(false) }
    var addCategoryQuickType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var categoryTabType by remember { mutableStateOf(CategoryType.EXPENSE) }

    // §5 回收站：软删 + 撤销 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUndo by remember { mutableStateOf<AppTransaction?>(null) }
    var showTagForm by remember { mutableStateOf(false) }
    LaunchedEffect(pendingUndo) {
        pendingUndo?.let { tx ->
            val result = snackbarHostState.showSnackbar(
                message = "已移入回收站",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreTransaction(tx.id)
            pendingUndo = null
        }
    }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(Tab.HOME, Tab.STATISTICS, Tab.CATEGORIES, Tab.HISTORY, Tab.SETTINGS).forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            if (tab != Tab.HOME) { isSearching = false; searchQuery = "" }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab != Tab.SETTINGS && selectedTab != Tab.CALENDAR && selectedTab != Tab.RECYCLE && selectedTab != Tab.TAGS && !isSearching) {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                Tab.HOME -> HomeScreen(
                    transactions = transactions,
                    categories = categories,
                    monthlyIncome = monthlyIncome,
                    monthlyExpense = monthlyExpense,
                    dailyExpense = dailyExpense,
                    monthlyBudget = monthlyBudget,
                    accounts = allAccounts,
                    templates = allTemplates,
                    recurringTransactions = allRecurring,
                    searchQuery = searchQuery,
                    isSearching = isSearching,
                    onSearchQueryChange = { searchQuery = it },
                    onToggleSearch = { isSearching = !isSearching },
                    onTransactionClick = { editingTransaction = it },
                    onDeleteTransaction = { tx -> viewModel.softDelete(tx); pendingUndo = tx },
                    transactionTagMap = viewModel.transactionTagMap.collectAsState().value,
                    onOpenCalendar = { selectedTab = Tab.CALENDAR },
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
                        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                        viewModel.addCategory(name, icon, ct)
                    },
                    onAddSubCategory = { name, icon, color, type, parentId ->
                        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                        viewModel.addCategory(name, icon, ct, parentId, color)
                    },
                    onDeleteCategory = { viewModel.deleteCategory(it) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
                    onMoveCategory = { category, delta -> viewModel.moveCategory(category, delta) },
                    showAddCategoryDialog = showAddCategoryDialogTab,
                    onDismissAddDialog = { showAddCategoryDialogTab = false },
                    onTypeChanged = { categoryTabType = if (it == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME }
                )
                Tab.SETTINGS -> SettingsScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = allAccounts,
                    templates = allTemplates,
                    recurringTransactions = allRecurring,
                    viewModel = viewModel,
                    onDataCleared = { viewModel.clearAllData() },
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onOpenTags = { selectedTab = Tab.TAGS }
                )
                Tab.HISTORY -> HistoryScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = allAccounts,
                    tags = viewModel.tags.collectAsState().value,
                    transactionTagMap = viewModel.transactionTagMap.collectAsState().value,
                    onTransactionClick = { editingTransaction = it },
                    onDeleteTransaction = { tx -> viewModel.softDelete(tx); pendingUndo = tx },
                    onOpenRecycle = { selectedTab = Tab.RECYCLE }
                )
                Tab.CALENDAR -> CalendarScreen(
                    transactions = transactions,
                    categories = categories,
                    onBack = { selectedTab = Tab.HOME }
                )
                Tab.RECYCLE -> RecycleBinScreen(
                    viewModel = viewModel,
                    categories = categories,
                    onBack = { selectedTab = Tab.HISTORY }
                )
                Tab.TAGS -> TagsScreen(
                    viewModel = viewModel,
                    onBack = { selectedTab = Tab.SETTINGS }
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
                accountBalances = viewModel.accountBalances.value,
                tags = viewModel.tags.value,
                initialTagIds = editingTransaction?.let {
                    viewModel.transactionTagMap.value[it.id]?.map { t -> t.id } ?: emptyList()
                } ?: emptyList(),
                onRequestAddTag = { showTagForm = true },
                onDismiss = {
                    showAddDialog = false
                    editingTransaction = null
                },
                onConfirm = { categoryId, amount, type, description, date, accountId, toAccountId, tagIds ->
                    if (editingTransaction != null) {
                        viewModel.updateTransaction(
                            editingTransaction!!.copy(
                                categoryId = categoryId,
                                amountCents = (amount * 100).toLong(),
                                type = type,
                                description = description,
                                date = date,
                                accountId = accountId,
                                // P1：非转账类型强制清空 toAccountId，避免「转账→收/支」切换后残留脏字段
                                toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null
                            ),
                            tagIds = tagIds
                        )
                    } else {
                        viewModel.addTransaction(categoryId, amount, type, description, date, accountId, toAccountId, tagIds)
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

        if (showTagForm) {
            TagFormDialog(
                onConfirm = { name, color, icon ->
                    viewModel.addTag(name, color, icon)
                    showTagForm = false
                },
                onDismiss = { showTagForm = false }
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