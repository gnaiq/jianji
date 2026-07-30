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
import timber.log.Timber
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.jianji.ui.components.AddTransactionDialog
import com.example.jianji.ui.components.CategoryFormDialog
import com.example.jianji.ui.components.TagFormDialog
import com.example.jianji.ui.screens.*
import com.example.jianji.ui.viewmodel.*
import com.example.jianji.data.Transaction as AppTransaction
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState

// §4 五页签底部 NavigationBar：图标 + 短标签，「分类管理」更名「分类」
// §10 Nav-Compose 单 Activity：用 NavHost + navController 管理全部屏幕路由，
//      底部 5 个主 tab 走 NavHost 的 startDestination 体系，日历/回收站/标签为次级路由。
enum class Tab(val label: String, val icon: ImageVector, val route: String) {
    HOME("首页", Icons.Filled.Home, "home"),
    STATISTICS("统计", Icons.Filled.BarChart, "statistics"),
    CATEGORIES("分类", Icons.Filled.Category, "categories"),
    HISTORY("历史", Icons.Filled.History, "history"),
    SETTINGS("设置", Icons.Filled.Settings, "settings"),
    CALENDAR("日历", Icons.Filled.DateRange, "calendar"),
    RECYCLE("回收站", Icons.Filled.DeleteSweep, "recycle"),
    TAGS("标签", Icons.Filled.Label, "tags")
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun JianjiApp(
    darkMode: Int = 0,
    onDarkModeChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val transactionVM: TransactionViewModel = koinViewModel()
    val categoryVM: CategoryViewModel = koinViewModel()
    val accountVM: AccountViewModel = koinViewModel()
    val budgetVM: BudgetViewModel = koinViewModel()
    val tagVM: TagViewModel = koinViewModel()
    val settingsVM: SettingsViewModel = koinViewModel()
    val transactions by transactionVM.transactions.collectAsState()
    val categories by categoryVM.categories.collectAsState()
    val monthlyIncome by transactionVM.monthlyIncome.collectAsState()
    val monthlyExpense by transactionVM.monthlyExpense.collectAsState()
    val dailyExpense by transactionVM.dailyExpense.collectAsState()
    val monthlyBudget by budgetVM.getMonthlyBudget(java.time.YearMonth.now()).collectAsState(initial = 0.0)
    val allAccounts by accountVM.allAccounts.collectAsState()
    val allTemplates by settingsVM.allTemplates.collectAsState()
    val allRecurring by settingsVM.recurringTransactions.collectAsState()

    // 周期备份排期由 JianjiApplication.onCreate 统一负责（KEEP 语义，此处重复调用已移除）
        // §1 P0 即时备份节流：跳过冷启动初始发射 + debounce 5s，
        // 连续记 N 笔账仅在停止操作 5 秒后合并为一次全量落盘（原实现每次发射都写 MediaStore）。
        // 覆盖交易/分类/账户/模板/周期记账变更（原实现仅监听交易，其余数据变更不触发备份）
        LaunchedEffect(Unit) {
            snapshotFlow { listOf(transactions, categories, allAccounts, allTemplates, allRecurring) }
                .drop(1)
                .debounce(5_000)
                .collect {
                    if (BackupScheduler.isImmediateBackupEnabled(context)) {
                        settingsVM.autoBackup()
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
            } catch (e: Exception) {
                Timber.w(e, "刷新桌面小组件失败")
            }
        }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Tab.HOME.route

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
            if (result == SnackbarResult.ActionPerformed) transactionVM.restoreTransaction(tx.id)
            pendingUndo = null
        }
    }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // 对话框 / 表单状态
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialogTab by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<AppTransaction?>(null) }
    var showAddCategoryQuick by remember { mutableStateOf(false) }
    var addCategoryQuickType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var categoryTabType by remember { mutableStateOf(CategoryType.EXPENSE) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(Tab.HOME, Tab.STATISTICS, Tab.CATEGORIES, Tab.HISTORY, Tab.SETTINGS).forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            if (tab.route != Tab.HOME.route) { isSearching = false; searchQuery = "" }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute != Tab.SETTINGS.route && currentRoute != Tab.CALENDAR.route &&
                currentRoute != Tab.RECYCLE.route && currentRoute != Tab.TAGS.route && !isSearching
            ) {
                FloatingActionButton(
                    onClick = {
                        if (currentRoute == Tab.CATEGORIES.route) {
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
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
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
                    onDeleteTransaction = { tx -> transactionVM.softDelete(tx); pendingUndo = tx },
                    transactionTagMap = tagVM.transactionTagMap.collectAsState().value,
                    onOpenCalendar = { navController.navigate(Tab.CALENDAR.route) },
                    onUseTemplate = { template ->
                        settingsVM.useTemplate(template.id)
                        transactionVM.addTransaction(
                            categoryId = template.categoryId,
                            amount = template.amount,
                            type = template.type,
                            description = template.description,
                            date = java.time.LocalDateTime.now(),
                            accountId = template.accountId
                        )
                    },
                    onProcessRecurring = { transactionVM.processRecurringDue() }
                )
            }
            composable(Tab.STATISTICS.route) {
                StatisticsScreen(
                    transactions = transactions,
                    categories = categories
                )
            }
            composable(Tab.CATEGORIES.route) {
                CategoryManagementScreen(
                    categories = categories,
                    onAddCategory = { name, icon, type ->
                        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                        categoryVM.addCategory(name, icon, ct)
                    },
                    onAddSubCategory = { name, icon, color, type, parentId ->
                        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                        categoryVM.addCategory(name, icon, ct, parentId, color)
                    },
                    onDeleteCategory = { categoryVM.deleteCategory(it) },
                    onUpdateCategory = { categoryVM.updateCategory(it) },
                    onMoveCategory = { category, delta -> categoryVM.moveCategory(category, delta) },
                    showAddCategoryDialog = showAddCategoryDialogTab,
                    onDismissAddDialog = { showAddCategoryDialogTab = false },
                    onTypeChanged = { categoryTabType = if (it == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME }
                )
            }
            composable(Tab.HISTORY.route) {
                HistoryScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = allAccounts,
                    tags = tagVM.tags.collectAsState().value,
                    transactionTagMap = tagVM.transactionTagMap.collectAsState().value,
                    onTransactionClick = { editingTransaction = it },
                    onDeleteTransaction = { tx -> transactionVM.softDelete(tx); pendingUndo = tx },
                    onOpenRecycle = { navController.navigate(Tab.RECYCLE.route) }
                )
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    transactions = transactions,
                    categories = categories,
                    accounts = allAccounts,
                    templates = allTemplates,
                    recurringTransactions = allRecurring,
                    transactionVM = transactionVM,
                    categoryVM = categoryVM,
                    accountVM = accountVM,
                    tagVM = tagVM,
                    settingsVM = settingsVM,
                    onDataCleared = {
                        settingsVM.clearAllData()
                    },
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onOpenTags = { navController.navigate(Tab.TAGS.route) }
                )
            }
            composable(Tab.CALENDAR.route) {
                CalendarScreen(
                    transactions = transactions,
                    categories = categories,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Tab.RECYCLE.route) {
                RecycleBinScreen(
                    transactionVM = transactionVM,
                    categories = categories,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Tab.TAGS.route) {
                TagsScreen(
                    tagVM = tagVM,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // 交易录入 / 标签 / 分类 弹层：状态驱动，独立于导航栈
        if (showAddDialog || editingTransaction != null) {
            AddTransactionDialog(
                categories = categories,
                editingTransaction = editingTransaction,
                templates = allTemplates,
                accounts = allAccounts,
                accountBalances = transactionVM.accountBalances.value,
                tags = tagVM.tags.value,
                initialTagIds = editingTransaction?.let {
                    tagVM.transactionTagMap.value[it.id]?.map { t -> t.id } ?: emptyList()
                } ?: emptyList(),
                onRequestAddTag = { showTagForm = true },
                onDismiss = {
                    showAddDialog = false
                    editingTransaction = null
                },
                onConfirm = { categoryId, amount, type, description, date, accountId, toAccountId, tagIds ->
                    if (editingTransaction != null) {
                        transactionVM.updateTransaction(
                            editingTransaction!!.copy(
                                categoryId = categoryId,
                                amountCents = Math.round(amount * 100),
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
                        transactionVM.addTransaction(categoryId, amount, type, description, date, accountId, toAccountId, tagIds)
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
                    tagVM.addTag(name, color, icon)
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
                    categoryVM.addCategory(name, icon, ct)
                    showAddCategoryQuick = false
                },
                onDismiss = { showAddCategoryQuick = false }
            )
        }
    }
}
