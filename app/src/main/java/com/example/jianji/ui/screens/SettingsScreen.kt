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
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.utils.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun SettingsScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    accounts: List<Account> = emptyList(),
    templates: List<QuickTemplate> = emptyList(),
    recurringTransactions: List<RecurringTransaction> = emptyList(),
    viewModel: TransactionViewModel? = null,
    onDataCleared: () -> Unit = {},
    darkMode: Int = 0,
    onDarkModeChange: (Int) -> Unit = {}
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
                // === 数据管理 ===
        item { SectionHeader("数据管理") }

        item {
            SettingsCard(
                icon = Icons.Default.CloudUpload,
                title = "Excel 导出",
                subtitle = "导出交易记录为 .xlsx 格式",
                enabled = !showExportProgress,
                onClick = {
                    if (showExportProgress) return@SettingsCard
                    showExportProgress = true
                    scope.launch {
                        try {
                            val all = viewModel?.getAllTransactionsSnapshot() ?: emptyList()
                            if (all.isEmpty()) {
                                Toast.makeText(context, "暂无数据可导出", Toast.LENGTH_SHORT).show()
                            } else {
                                val result = excelExportManager.exportToExcel(all, categories, accounts)
                                excelExportManager.shareFile(result.file)
                                Toast.makeText(context, "导出成功(${result.recordCount}条): ${result.file.name}", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            showExportProgress = false
                        }
                    }
                }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Backup,
                title = "备份数据库",
                subtitle = "导出完整数据(JSON)到下载目录",
                onClick = {
                    ensureStoragePermission {
                        scope.launch {
                            try {
                                val db = JianjiDatabase.getDatabase(context.applicationContext)
                                val hasData = db.transactionDao().getAllSnapshot().isNotEmpty()
                                    || db.categoryDao().getAllCategories().first().isNotEmpty()
                                    || db.accountDao().getAll().isNotEmpty()
                                if (!hasData) {
                                    Toast.makeText(context, "暂无数据可备份", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val json = DataImportManager().generateExportJson(db)
                                val fileName = "简记备份_${LocalDate.now()}.json"
                                val savedName = BackupStorage.save(context, fileName, "application/json", json)
                                Toast.makeText(context, "备份成功: $savedName", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.CloudDownload,
                title = "恢复备份",
                subtitle = "从 JSON 备份文件恢复数据",
                onClick = { showImportDialog = true }
            )
        }

        // === 自动备份 ===
        item {
            var autoBackupDays by remember { mutableIntStateOf(BackupScheduler.getIntervalDays(context)) }
            val autoBackupEnabled = autoBackupDays > 0
            var immediateBackup by remember { mutableStateOf(BackupScheduler.isImmediateBackupEnabled(context)) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("自动备份", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (autoBackupEnabled) "已启用：每${
                                    when (autoBackupDays) { 1 -> "天"; 7 -> "周"; 30 -> "月"; else -> "$autoBackupDays 天" }
                                }自动备份一次" else "未启用（数据变更时仍会即时备份）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(checked = autoBackupEnabled, onCheckedChange = { on ->
                            autoBackupDays = if (on) BackupScheduler.DEFAULT_DAYS else 0
                            BackupScheduler.saveIntervalDays(context, autoBackupDays)
                        })
                    }
                    if (autoBackupEnabled) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "每天", 7 to "每周", 30 to "每月").forEach { (days, label) ->
                                FilterChip(
                                    selected = autoBackupDays == days,
                                    onClick = {
                                        autoBackupDays = days
                                        BackupScheduler.saveIntervalDays(context, days)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("数据变更即时备份", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (immediateBackup) "已开启：数据变更时自动备份" else "已关闭：数据变更不再自动备份",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(checked = immediateBackup, onCheckedChange = { on ->
                            immediateBackup = on
                            BackupScheduler.setImmediateBackupEnabled(context, on)
                        })
                    }
                }
            }
        }

        item {
            SettingsCard(
                icon = Icons.Default.Backup,
                title = "CSV 备份",
                subtitle = "导出 CSV 格式到下载目录",
                onClick = {
                    ensureStoragePermission {
                        scope.launch {
                            try {
                                val all = viewModel?.getAllTransactionsSnapshot() ?: emptyList()
                                if (all.isEmpty()) {
                                    Toast.makeText(context, "暂无数据可备份", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val csv = buildString {
                                appendLine("ID,日期,类型,分类ID,金额,描述")
                                all.sortedByDescending { it.date }.forEach { tx ->
                                    appendLine("${tx.id},${tx.date},${tx.type},${tx.categoryId},${tx.amount},${tx.description}")
                                }
                            }
                            val fileName = "简记备份_${LocalDate.now()}.csv"
                            val savedName = BackupStorage.save(context, fileName, "text/csv", csv)
                            Toast.makeText(context, "备份成功: $savedName", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.DeleteSweep,
                title = "管理备份",
                subtitle = "查看并删除下载目录中的旧备份文件",
                onClick = { showBackupManage = true }
            )
        }

        item {
            SettingsCard(
                icon = Icons.Default.Delete,
                title = "清除所有数据",
                subtitle = "删除所有交易、分类和设置",
                color = MaterialTheme.colorScheme.error,
                onClick = { showClearDialog = true }
            )
        }

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
                // === 关于 ===
        item { SectionHeader("关于 & 更新") }

        item {
            SettingsCard(
                icon = Icons.Default.SystemUpdate,
                title = updateStatus,
                subtitle = "当前版本: ${BuildConfig.VERSION_NAME}",
            onClick = {
                updateStatus = "检查中..."
                scope.launch {
                    val result = updateManager.checkForUpdate()
                    result.onSuccess { info ->
                        if (info == null) {
                            updateStatus = "当前已是最新版本"
                        } else {
                            updateStatus = "发现新版本 v${info.versionName}，开始下载…"
                            downloadProgress = 0
                            try {
                                updateManager.downloadAndInstall(info.downloadUrl) { p ->
                                    downloadProgress = p
                                    updateStatus = "正在下载更新：$p%"
                                }
                                updateStatus = "下载完成，正在安装…"
                                downloadProgress = 0
                            } catch (e: Exception) {
                                updateStatus = "下载失败: ${e.message ?: "未知错误"}"
                                downloadProgress = 0
                            }
                        }
                    }.onFailure { e ->
                        // 检查失败，但本机可能已下好安装包 → 直接复用安装
                        if (updateManager.hasLocalApk()) {
                            updateStatus = "检测到本机已有新版本安装包，正在安装…"
                            updateManager.installLocalApk()
                        } else {
                            updateStatus = "检查失败: ${e.message}"
                            Toast.makeText(
                                context,
                                "可前往 GitHub 手动下载：${updateManager.releasesUrl()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            )
        }

        if (downloadProgress in 1..99) {
            item {
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TextButton(onClick = { updateManager.cancelDownload() }) { Text("取消下载") }
                }
            }
        }

        item {
            SettingsCard(
                icon = Icons.Default.Info,
                title = "关于简记",
                subtitle = "v${BuildConfig.VERSION_NAME} | 记录每一笔 · 让生活更有数",
                onClick = {}
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // === Dialogs ===

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清除") },
            text = { Text("将删除所有交易记录、自定义分类和设置。此操作不可撤销！") },
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
            viewModel = viewModel,
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
            viewModel = viewModel,
            accountBalances = viewModel?.accountBalances?.value ?: emptyMap(),
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showTemplateDialog) {
        TemplateManagementDialog(
            templates = templates,
            categories = categories,
            viewModel = viewModel,
            onDismiss = { showTemplateDialog = false }
        )
    }

    if (showRecurringDialog) {
        RecurringManagementDialog(
            recurringTransactions = recurringTransactions,
            categories = categories,
            accounts = accounts,
            viewModel = viewModel,
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
            viewModel = viewModel,
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

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

// ======== Budget Dialog ========
@Composable
fun BudgetSettingsDialog(viewModel: TransactionViewModel?, onDismiss: () -> Unit) {
    val year = YearMonth.now().year
    val month = YearMonth.now().monthValue
    var budgetAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预算设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("设定 ${year}年${month}月 月度预算", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = budgetAmount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) budgetAmount = it },
                    label = { Text("预算金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = budgetAmount.toDoubleOrNull() ?: return@Button
                scope.launch {
                    viewModel?.setBudget(Budget(
                        amount = amt, period = BudgetPeriod.MONTHLY,
                        year = year, month = month
                    ))
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ======== Account Dialog ========
@Composable
fun AccountManagementDialog(accounts: List<Account>, viewModel: TransactionViewModel?, onDismiss: () -> Unit, accountBalances: Map<Long, Double> = emptyMap()) {
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf("💳") }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账户管理") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { acc ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { },
                        colors = CardDefaults.cardColors(
                            containerColor = if (acc.isDefault) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(acc.icon, style = MaterialTheme.typography.bodyLarge)
                                Column {
                                    Text(acc.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("余额 ¥%.2f".format(accountBalances[acc.id] ?: 0.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    if (acc.isDefault) Text("默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row {
                                if (!acc.isDefault) {
                                    TextButton(onClick = { viewModel?.setDefaultAccount(acc.id) }) { Text("默认") }
                                }
                                if (accounts.size > 1 && !acc.isDefault) {
                                    TextButton(onClick = { pendingDelete = acc },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                                }
                            }
                        }
                    }
                }
                if (showAdd) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("账户名称") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    val iconOptions = listOf("💵","💬","🔵","🏦","💰","💳","🪙","📱","💲")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        iconOptions.forEach { i ->
                            Text(i, modifier = Modifier.clickable { newIcon = i }, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                } else {
                    TextButton(onClick = { showAdd = true }) { Text("+ 添加账户") }
                }
            }
        },
        confirmButton = {
            if (showAdd) {
                Button(onClick = {
                    if (newName.isNotBlank()) { viewModel?.addAccount(newName, newIcon); showAdd = false; newName = "" }
                }, enabled = newName.isNotBlank()) { Text("添加") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除账户") },
            text = { Text("删除「${pendingDelete?.name}」后，其下的交易将解除账户关联（不再归属任何账户），但交易本身不会被删除。确定继续？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.deleteAccountCascade(pendingDelete!!)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

// ======== Template Dialog ========
@Composable
fun TemplateManagementDialog(
    templates: List<QuickTemplate>,
    categories: List<Category>,
    viewModel: TransactionViewModel?,
    onDismiss: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var tmpAmount by remember { mutableStateOf("") }
    var tmpDesc by remember { mutableStateOf("") }
    var tmpCatId by remember { mutableStateOf<Long?>(null) }
    var tmpType by remember { mutableStateOf(TransactionType.EXPENSE) }
    val tmpCt = if (tmpType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
    val filteredCats = categories.filter { it.type == tmpCt }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快捷模板") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (templates.isEmpty() && !showAdd) {
                    Text("暂无模板，点击下方按钮创建", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                templates.forEach { t ->
                    val cat = categories.find { it.id == t.categoryId }
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(cat?.icon ?: "📁")
                                Column {
                                    Text(t.description.ifEmpty { cat?.name ?: "" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("${if (t.type == TransactionType.EXPENSE) "-" else "+"}¥${t.amount} · 使用${t.useCount}次",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            TextButton(onClick = { viewModel?.deleteTemplate(t) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                        }
                    }
                }
                if (showAdd) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { tmpType = TransactionType.INCOME }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tmpType == TransactionType.INCOME) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant)) { Text("收入") }
                        Button(onClick = { tmpType = TransactionType.EXPENSE }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tmpType == TransactionType.EXPENSE) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.surfaceVariant)) { Text("支出") }
                    }
                    OutlinedTextField(value = tmpAmount, onValueChange = { tmpAmount = it }, label = { Text("金额") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = tmpDesc, onValueChange = { tmpDesc = it }, label = { Text("描述") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("选择分类", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(filteredCats) { cat ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { tmpCatId = cat.id },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tmpCatId == cat.id) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text("${cat.icon} ${cat.name}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { showAdd = true }) { Text("+ 创建模板") }
                }
            }
        },
        confirmButton = {
            if (showAdd) {
                Button(onClick = {
                    val amt = tmpAmount.toDoubleOrNull() ?: return@Button
                    val catId = tmpCatId ?: return@Button
                    viewModel?.addTemplate(QuickTemplate(categoryId = catId, amount = amt, type = tmpType, description = tmpDesc))
                    showAdd = false; tmpAmount = ""; tmpDesc = ""; tmpCatId = null
                }, enabled = tmpAmount.toDoubleOrNull() != null && tmpCatId != null) { Text("添加") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// 下次执行时间计算已抽到 com.example.jianji.utils.computeRecurringNextRun（纯函数，便于测试）

// ======== Recurring Dialog ========
@Composable
fun RecurringManagementDialog(
    recurringTransactions: List<RecurringTransaction>,
    categories: List<Category>,
    accounts: List<Account>,
    viewModel: TransactionViewModel?,
    onDismiss: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var rCatId by remember { mutableStateOf<Long?>(null) }
    var rAmount by remember { mutableStateOf("") }
    var rDesc by remember { mutableStateOf("") }
    var rType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var rFreq by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var rDayOfMonth by remember { mutableStateOf("1") }
    var rInterval by remember { mutableStateOf("1") }
    var rDayOfWeek by remember { mutableStateOf("1") }
    var rMonthOfYear by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周期交易") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showAdd) {
                    Text("到期的周期交易会自动生成交易记录", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    if (recurringTransactions.isEmpty()) {
                        Text("暂无周期交易，点击下方按钮添加", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                if (!showAdd) {
                recurringTransactions.forEach { rt ->
                    val cat = categories.find { it.id == rt.categoryId }
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${cat?.icon ?: "📁"} ${rt.description.ifEmpty { cat?.name ?: "" }}",
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("${if (rt.type == TransactionType.EXPENSE) "-" else "+"}¥${rt.amount} · ${rt.frequency.name} · 下次: ${rt.nextRunDate.format(DateTimeFormatter.ofPattern("MM/dd"))}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            TextButton(onClick = { viewModel?.deleteRecurring(rt) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                        }
                    }
                }
                }
                if (showAdd) {
                    OutlinedTextField(value = rAmount, onValueChange = { rAmount = it }, label = { Text("金额") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = rDesc, onValueChange = { rDesc = it }, label = { Text("描述（可选）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { rType = TransactionType.INCOME }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (rType == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) { Text("收入") }
                        Button(onClick = { rType = TransactionType.EXPENSE }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (rType == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant)) { Text("支出") }
                    }
                    Text("周期", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RecurringFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = rFreq == freq, onClick = { rFreq = freq },
                                label = { Text(freq.name, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    val unitLabel = when (rFreq) {
                        RecurringFrequency.DAILY -> "天"
                        RecurringFrequency.WEEKLY -> "周"
                        RecurringFrequency.MONTHLY -> "月"
                        RecurringFrequency.YEARLY -> "年"
                    }
                    if (rFreq == RecurringFrequency.MONTHLY || rFreq == RecurringFrequency.YEARLY) {
                        OutlinedTextField(value = rDayOfMonth, onValueChange = {
                            if (it.all { c -> c.isDigit() }) rDayOfMonth = it
                        }, label = { Text(if (rFreq == RecurringFrequency.YEARLY) "每年几号" else "每月几号") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    if (rFreq == RecurringFrequency.YEARLY) {
                        OutlinedTextField(value = rMonthOfYear, onValueChange = {
                            if (it.all { c -> c.isDigit() }) rMonthOfYear = it
                        }, label = { Text("每年几月") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    if (rFreq == RecurringFrequency.WEEKLY) {
                        Text("每${unitLabel}的星期几", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                            weekLabels.forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = (rDayOfWeek.toIntOrNull() ?: 1) == idx + 1,
                                    onClick = { rDayOfWeek = (idx + 1).toString() },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = rInterval, onValueChange = {
                        if (it.all { c -> c.isDigit() }) rInterval = it
                    }, label = { Text("间隔（每 N 个${unitLabel}执行一次）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("选择分类", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                        val rCt = if (rType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                        items(categories.filter { it.type == rCt }) { cat ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { rCatId = cat.id },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (rCatId == cat.id) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) { Text("${cat.icon} ${cat.name}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    val previewNext = computeRecurringNextRun(
                        rFreq, rDayOfMonth.toIntOrNull() ?: 1, rInterval.toIntOrNull() ?: 1,
                        rDayOfWeek.toIntOrNull() ?: 1, rMonthOfYear.toIntOrNull() ?: 1
                    )
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("下次记账: ${previewNext.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAdd = false; rAmount = ""; rDesc = ""; rCatId = null }) { Text("取消") }
                    }
                } else {
                    TextButton(onClick = { showAdd = true }) { Text("+ 添加周期交易") }
                }
            }
        },
        confirmButton = {
            if (showAdd) {
                Button(onClick = {
                    val amt = rAmount.toDoubleOrNull() ?: return@Button
                    val catId = rCatId ?: return@Button
                    val dom = rDayOfMonth.toIntOrNull() ?: 1
                    val interval = rInterval.toIntOrNull() ?: 1
                    val dow = rDayOfWeek.toIntOrNull() ?: 1
                    val nextRun = computeRecurringNextRun(rFreq, dom, interval, dow)
                    viewModel?.addRecurring(RecurringTransaction(
                        categoryId = catId, amount = amt, type = rType, description = rDesc,
                        frequency = rFreq, interval = interval, dayOfMonth = dom,
                        monthOfYear = rMonthOfYear.toIntOrNull() ?: 1,
                        dayOfWeek = dow, nextRunDate = nextRun
                    ))
                    showAdd = false; rAmount = ""; rDesc = ""; rCatId = null
                }, enabled = rAmount.toDoubleOrNull() != null && rCatId != null) { Text("添加") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ======== Annual Poster ========
@Composable
fun AnnualPosterDialog(
    posterGenerator: PosterGenerator,
    transactions: List<Transaction>,
    categories: List<Category>,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val candidateYears = remember(transactions) {
        val set = transactions.map { it.date.year }.toMutableSet()
        set.add(LocalDate.now().year)
        set.sortedDescending()
    }
    var selectedYear by remember(candidateYears) { mutableStateOf(candidateYears.first()) }
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("年度账单海报") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择年份生成年度账单分享海报", style = MaterialTheme.typography.bodyMedium)
                Text("年份", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    candidateYears.forEach { y ->
                        FilterChip(
                            selected = selectedYear == y,
                            onClick = { selectedYear = y },
                            label = { Text(y.toString()) }
                        )
                    }
                }
                Button(
                    onClick = {
                        isGenerating = true
                        scope.launch {
                            try {
                                val uri = posterGenerator.generatePoster(transactions, categories, selectedYear)
                                try {
                                    posterGenerator.sharePoster(uri)
                                    onDismiss()
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "海报已生成，但未找到可分享的应用", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } catch (e: Throwable) {
                                Toast.makeText(context, "生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isGenerating) "生成中..." else "生成并分享")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ======== Import Dialog ========
@Composable
fun ImportDialog(
    viewModel: TransactionViewModel?,
    ensureStoragePermission: (() -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 执行恢复：清空现有交易并按备份重新写入（替换语义）
    val doImport: () -> Unit = {
        if (jsonText.isNotBlank()) {
            ensureStoragePermission {
                importing = true
                scope.launch {
                    try {
                        val importer = DataImportManager()
                        val result = importer.importFromJson(
                            jsonText, JianjiDatabase.getDatabase(context.applicationContext)
                        )
                        importing = false
                        if (result.transactionCount > 0) {
                            val detail = if (result.isFullRestore) "（已恢复账户/预算/周期/模板）"
                                else "（旧格式备份，仅恢复交易+分类）"
                            Toast.makeText(context, "恢复成功，导入 ${result.transactionCount} 笔$detail", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "未导入数据，请检查文件格式", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        importing = false
                        Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 自动检测共享下载目录中的备份文件（卸载后保留）
    LaunchedEffect(Unit) {
        backups = BackupStorage.list(context)
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                jsonText = stream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (backups.isNotEmpty()) {
                    Text("检测到以下备份（点击选择）", style = MaterialTheme.typography.labelMedium)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(4.dp)
                        ) {
                            items(backups) { entry ->
                                val sizeKb = (entry.size / 1024.0).let { if (it < 1) "<1" else "%.1f".format(it) }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        try {
                                            jsonText = BackupStorage.read(context, entry.uri)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("${sizeKb}KB", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                } else {
                    Text("未检测到备份，可手动选择文件或粘贴 JSON 数据",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { filePicker.launch("application/json") }) {
                    Text("选择备份文件")
                }
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    label = { Text("JSON 数据") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { showRestoreConfirm = true },
                enabled = jsonText.isNotBlank() && !importing
            ) { Text(if (importing) "恢复中..." else "恢复") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复备份") },
            text = { Text("恢复将清空当前所有交易记录并按备份重新写入，且不可撤销。确定要从该备份恢复吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        doImport()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确定恢复") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
fun BackupManagementDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var backups by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var toDelete by remember { mutableStateOf<BackupFileEntry?>(null) }
    var showDeleteAll1 by remember { mutableStateOf(false) }
    var showDeleteAll2 by remember { mutableStateOf(false) }
    var verifyText by remember { mutableStateOf("") }

    fun refresh() { backups = BackupStorage.list(context) }
    LaunchedEffect(Unit) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下载目录中的备份文件，可删除不再需要的旧备份", style = MaterialTheme.typography.bodyMedium)
                if (backups.isEmpty()) {
                    Text("暂无备份文件", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(4.dp)) {
                        items(backups) { entry ->
                            val sizeKb = (entry.size / 1024.0).let { if (it < 1) "<1" else "%.1f".format(it) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${sizeKb}KB", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                IconButton(onClick = { toDelete = entry }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.padding(end = 8.dp)) {
                if (backups.isNotEmpty()) {
                    TextButton(
                        onClick = { showDeleteAll1 = true; verifyText = "" },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除全部") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定删除备份「${toDelete!!.name}」？删除后不可恢复。") },
            confirmButton = {
                Button(onClick = {
                    val entry = toDelete!!
                    try {
                        BackupStorage.delete(context, entry.uri)
                        Toast.makeText(context, "已删除: ${entry.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    toDelete = null
                    refresh()
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }

    if (showDeleteAll1) {
        AlertDialog(
            onDismissRequest = { showDeleteAll1 = false },
            title = { Text("删除全部备份") },
            text = { Text("确定要删除全部 ${backups.size} 个备份文件吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAll1 = false
                        showDeleteAll2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll1 = false }) { Text("取消") } }
        )
    }

    if (showDeleteAll2) {
        AlertDialog(
            onDismissRequest = { showDeleteAll2 = false },
            title = { Text("二次确认") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入「删除」以确认删除全部备份：")
                    OutlinedTextField(
                        value = verifyText,
                        onValueChange = { verifyText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        BackupStorage.deleteAll(context)
                        Toast.makeText(context, "已删除全部 ${backups.size} 个备份", Toast.LENGTH_SHORT).show()
                        verifyText = ""
                        showDeleteAll2 = false
                        refresh()
                    },
                    enabled = verifyText == "删除",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll2 = false }) { Text("取消") } }
        )
    }
}
