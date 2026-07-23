package com.example.jianji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jianji.data.*
import com.example.jianji.ui.components.AddTransactionDialog
import com.example.jianji.utils.AppLockManager
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

    // === App 锁门禁 ===
    val appLockManager = remember { AppLockManager(context) }
    var isUnlocked by remember { mutableStateOf(!appLockManager.isLockEnabled || !appLockManager.hasPin) }
    var showPinDialog by remember { mutableStateOf(false) }
    var authInProgress by remember { mutableStateOf(false) }
    var lockError by remember { mutableStateOf<String?>(null) }

    fun requestUnlock() {
        if (authInProgress) return
        if (!appLockManager.isLockEnabled || !appLockManager.hasPin) {
            isUnlocked = true
            return
        }
        val activity = context as? FragmentActivity ?: run { isUnlocked = true; return }
        authInProgress = true
        lockError = null
        if (appLockManager.canUseBiometric()) {
            appLockManager.authenticate(
                activity,
                onSuccess = { authInProgress = false; isUnlocked = true },
                onError = { authInProgress = false; lockError = it },
                onPinFallback = { authInProgress = false; showPinDialog = true }
            )
        } else {
            authInProgress = false
            showPinDialog = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (appLockManager.isLockEnabled && appLockManager.hasPin && !isUnlocked) {
                    requestUnlock()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (appLockManager.isLockEnabled && appLockManager.hasPin) requestUnlock()
    }

    if (!isUnlocked) {
        LockScreen(
            onUnlock = { requestUnlock() },
            error = lockError,
            useBiometric = appLockManager.canUseBiometric()
        )
        if (showPinDialog) {
            PinUnlockDialog(
                onDismiss = { showPinDialog = false },
                onVerified = { showPinDialog = false; lockError = null; isUnlocked = true },
                verify = { pin -> appLockManager.verifyPin(pin) }
            )
        }
        return
    }

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
                        val ct = if (type == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                        viewModel.addCategory(name, icon, ct)
                    },
                    onDeleteCategory = { viewModel.deleteCategory(it) },
                    onUpdateCategory = { viewModel.updateCategory(it) },
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

@Composable
fun LockScreen(onUnlock: () -> Unit, error: String?, useBiometric: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Lock, contentDescription = null,
                modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary
            )
            Text("简记", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "应用已锁定", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onUnlock) {
                Icon(
                    if (useBiometric) Icons.Default.Fingerprint else Icons.Default.Lock,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (useBiometric) "指纹/面部解锁" else "使用密码解锁")
            }
        }
    }
}

@Composable
fun PinUnlockDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    verify: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; wrong = false }
                    },
                    label = { Text("4-6 位数字密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = wrong
                )
                if (wrong) {
                    Text("密码错误", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (verify(pin)) onVerified() else wrong = true }, enabled = pin.length >= 4) {
                Text("解锁")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
