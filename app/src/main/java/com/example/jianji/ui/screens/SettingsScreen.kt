package com.example.jianji.ui.screens

import android.Manifest
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import com.example.jianji.ui.screens.settings.*
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.ui.viewmodel.CategoryViewModel
import com.example.jianji.ui.viewmodel.AccountViewModel
import com.example.jianji.ui.viewmodel.BudgetViewModel
import com.example.jianji.ui.viewmodel.TagViewModel
import com.example.jianji.ui.viewmodel.SettingsViewModel
import com.example.jianji.core.common.PosterGenerator
import com.example.jianji.core.io.ExcelExportManager
import com.example.jianji.core.update.UpdateManager
import com.example.jianji.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private sealed interface SettingsDestination {
    data object Main : SettingsDestination
    data object DataManagement : SettingsDestination
    data object FunctionManagement : SettingsDestination
    data object Appearance : SettingsDestination
    data object About : SettingsDestination
}

@Composable
fun SettingsScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    accounts: List<Account> = emptyList(),
    templates: List<QuickTemplate> = emptyList(),
    recurringTransactions: List<RecurringTransaction> = emptyList(),
    transactionVM: TransactionViewModel? = null,
    categoryVM: CategoryViewModel? = null,
    accountVM: AccountViewModel? = null,
    budgetVM: BudgetViewModel? = null,
    tagVM: TagViewModel? = null,
    settingsVM: SettingsViewModel? = null,
    onDataCleared: () -> Unit = {},
    darkMode: Int = 0,
    onDarkModeChange: (Int) -> Unit = {},
    onOpenTags: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showBackupManage by remember { mutableStateOf(false) }
    var showPosterDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }

    var showExportProgress by remember { mutableStateOf(false) }

    // 备份/恢复在 Android 6–9（<Q）需要运行时存储权限；Q+ 走 MediaStore 无需权限（P0-3）
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.entries.all { it.value }
        if (granted) pendingStorageAction?.invoke()
        else Toast.makeText(context, "需要存储权限才能读写备份文件", Toast.LENGTH_SHORT).show()
        pendingStorageAction = null
    }
    val ensureStoragePermission: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
        } else {
            val needed = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) action()
            else {
                pendingStorageAction = action
                storagePermissionLauncher.launch(needed.toTypedArray())
            }
        }
    }

    val updateManager = remember { UpdateManager(context) }
    val excelExportManager = remember { ExcelExportManager(context) }
    val posterGenerator = remember { PosterGenerator(context) }
    var updateStatus by remember { mutableStateOf("检查更新") }
    var downloadProgress by remember { mutableIntStateOf(0) }

    var destination by remember { mutableStateOf<SettingsDestination>(SettingsDestination.Main) }

    when (destination) {
        SettingsDestination.Main -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                item { SectionHeader("设置分组") }
                item {
                    SettingsCard(
                        icon = Icons.Default.Backup,
                        title = "数据管理",
                        subtitle = "备份、恢复、导出与清除",
                        onClick = { destination = SettingsDestination.DataManagement }
                    )
                }
                item {
                    SettingsCard(
                        icon = Icons.Default.AccountBalance,
                        title = "功能管理",
                        subtitle = "预算、账户、模板、周期记账",
                        onClick = { destination = SettingsDestination.FunctionManagement }
                    )
                }
                item {
                    SettingsCard(
                        icon = Icons.Default.DarkMode,
                        title = "外观",
                        subtitle = "深色模式",
                        onClick = { destination = SettingsDestination.Appearance }
                    )
                }
                item {
                    SettingsCard(
                        icon = Icons.Default.Info,
                        title = "关于 & 更新",
                        subtitle = "版本检查与关于",
                        onClick = { destination = SettingsDestination.About }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        SettingsDestination.DataManagement -> {
            SettingsSubScaffold(title = "数据管理", onBack = { destination = SettingsDestination.Main }) {
                dataManagementSection(
                    scope = scope,
                    context = context,
                    transactionVM = transactionVM,
                    categories = categories,
                    accounts = accounts,
                    excelExportManager = excelExportManager,
                    showExportProgress = showExportProgress,
                    onExportProgressChange = { showExportProgress = it },
                    ensureStoragePermission = ensureStoragePermission,
                    onShowImport = { showImportDialog = true },
                    onShowBackupManage = { showBackupManage = true },
                    onShowClear = { showClearDialog = true }
                )
            }
        }

        SettingsDestination.FunctionManagement -> {
            SettingsSubScaffold(title = "功能管理", onBack = { destination = SettingsDestination.Main }) {
                // === 功能管理 ===
        item { SectionHeader("功能管理") }

        item {
            SettingsCard(
                icon = Icons.Default.AccountBalance,
                title = "预算设置",
                subtitle = "设定月度/年度预算上限",
                onClick = { showBudgetDialog = true }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.AccountBalanceWallet,
                title = "账户管理",
                subtitle = "管理支付账户（微信/支付宝/现金/银行卡等）",
                onClick = { showAccountDialog = true }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Bookmark,
                title = "快捷模板",
                subtitle = "管理常用交易模板",
                onClick = { showTemplateDialog = true }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Sync,
                title = "周期交易",
                subtitle = "自动记账（房租/工资/订阅等）",
                onClick = { showRecurringDialog = true }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Label,
                title = "标签管理",
                subtitle = "创建并管理交易标签，记账时打标、按标签筛选",
                onClick = onOpenTags
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Style,
                title = "年度账单海报",
                subtitle = "生成分享用年度账单图片",
                onClick = { showPosterDialog = true }
            )
        }

            }
        }

        SettingsDestination.Appearance -> {
            SettingsSubScaffold(title = "外观", onBack = { destination = SettingsDestination.Main }) {
                // === 外观 ===
        item { SectionHeader("外观") }

        item {
            SettingsCard(
                icon = Icons.Default.DarkMode,
                title = "深色模式",
                subtitle = when (darkMode) { 0 -> "跟随系统"; 1 -> "浅色"; 2 -> "深色"; else -> "跟随系统" },
                onClick = { showDarkModeDialog = true }
            )
        }

            }
        }

        SettingsDestination.About -> {
            SettingsSubScaffold(title = "关于 & 更新", onBack = { destination = SettingsDestination.Main }) {
                aboutSection(
                    scope = scope,
                    context = context,
                    updateManager = updateManager,
                    updateStatus = updateStatus,
                    onUpdateStatusChange = { updateStatus = it },
                    downloadProgress = downloadProgress,
                    onDownloadProgressChange = { downloadProgress = it }
                )
            }
        }
    }

    // === Dialogs ===

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清除") },
            text = { Text("将清空全部交易、分类、账户、预算、周期交易与模板，并恢复为默认分类与默认账户（应用设置如深色模式将保留）。此操作不可撤销！") },
            confirmButton = {
                Button(onClick = { onDataCleared(); showClearDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("确认清除")
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } }
        )
    }

    if (showBudgetDialog) {
        BudgetSettingsDialog(
            budgetVM = budgetVM,
            onDismiss = { showBudgetDialog = false }
        )
    }

    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text("深色模式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onDarkModeChange(mode); showDarkModeDialog = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = darkMode == mode,
                                onClick = { onDarkModeChange(mode); showDarkModeDialog = false }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDarkModeDialog = false }) { Text("关闭") } }
        )
    }

    if (showAccountDialog) {
        AccountManagementDialog(
            accounts = accounts,
            accountVM = accountVM,
            accountBalances = transactionVM?.accountBalances?.value ?: emptyMap(),
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showTemplateDialog) {
        TemplateManagementDialog(
            templates = templates,
            categories = categories,
            settingsVM = settingsVM,
            onDismiss = { showTemplateDialog = false }
        )
    }

    if (showRecurringDialog) {
        RecurringManagementDialog(
            recurringTransactions = recurringTransactions,
            categories = categories,
            accounts = accounts,
            settingsVM = settingsVM,
            onDismiss = { showRecurringDialog = false }
        )
    }

    if (showPosterDialog) {
        AnnualPosterDialog(
            posterGenerator = posterGenerator,
            transactions = transactions,
            categories = categories,
            onDismiss = { showPosterDialog = false }
        )
    }

    if (showImportDialog) {
        ImportDialog(
            ensureStoragePermission = ensureStoragePermission,
            onDismiss = { showImportDialog = false }
        )
    }

    if (showBackupManage) {
        BackupManagementDialog(
            onDismiss = { showBackupManage = false }
        )
    }
}
