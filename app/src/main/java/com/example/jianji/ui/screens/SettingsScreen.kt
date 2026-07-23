package com.example.jianji.ui.screens

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.input.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.utils.*
import com.example.jianji.BuildConfig
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    accounts: List<Account> = emptyList(),
    templates: List<QuickTemplate> = emptyList(),
    recurringTransactions: List<RecurringTransaction> = emptyList(),
    viewModel: TransactionViewModel? = null,
    onDataCleared: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showLockSetup by remember { mutableStateOf(false) }
    var showPosterDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showExportProgress by remember { mutableStateOf(false) }

    val updateManager = remember { UpdateManager(context) }
    val excelExportManager = remember { ExcelExportManager(context) }
    val appLockManager = remember { AppLockManager(context) }
    val posterGenerator = remember { PosterGenerator(context) }
    var updateStatus by remember { mutableStateOf("检查更新") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

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
                    scope.launch {
                        try {
                            val all = viewModel?.getAllTransactionsSnapshot() ?: emptyList()
                            if (all.isEmpty()) {
                                Toast.makeText(context, "暂无数据可备份", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val json = DataImportManager().generateExportJson(all, categories)
                            val fileName = "简记备份_${LocalDate.now()}.json"
                            val savedName = BackupStorage.save(context, fileName, "application/json", json)
                            Toast.makeText(context, "备份成功: $savedName", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
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

        item {
            SettingsCard(
                icon = Icons.Default.Backup,
                title = "CSV 备份",
                subtitle = "导出 CSV 格式到下载目录",
                onClick = {
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

        // === 安全 ===
        item { SectionHeader("安全") }

        item {
            val isLocked = appLockManager.isLockEnabled
            SettingsCard(
                icon = Icons.Default.Lock,
                title = "App 锁",
                subtitle = if (isLocked) "已启用指纹/PIN 锁" else "启动时验证指纹或密码",
                onClick = { showLockSetup = true }
            )
        }

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
                                updateStatus = "发现新版本 v${info.versionName}，正在下载并自动安装…"
                                try {
                                    updateManager.downloadAndInstall(info.downloadUrl)
                                } catch (e: Exception) {
                                    updateStatus = "下载失败: ${e.message}"
                                }
                            }
                        }.onFailure { e ->
                            updateStatus = "检查失败: ${e.message}"
                        }
                    }
                }
            )
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

    if (showAccountDialog) {
        AccountManagementDialog(
            accounts = accounts,
            viewModel = viewModel,
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

    if (showLockSetup) {
        AppLockSetupDialog(
            appLockManager = appLockManager,
            onDismiss = { showLockSetup = false }
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
            onDismiss = { showImportDialog = false }
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
fun AccountManagementDialog(accounts: List<Account>, viewModel: TransactionViewModel?, onDismiss: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf("💳") }

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
                                    if (acc.isDefault) Text("默认", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Row {
                                if (!acc.isDefault) {
                                    TextButton(onClick = { viewModel?.setDefaultAccount(acc.id) }) { Text("默认") }
                                }
                                if (accounts.size > 1 && !acc.isDefault) {
                                    TextButton(onClick = { viewModel?.deleteAccount(acc) },
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
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

// 计算周期交易的下次执行时间（供预览与保存复用，避免两处逻辑不一致）
private fun computeRecurringNextRun(
    freq: RecurringFrequency,
    dayOfMonth: Int,
    interval: Int
): LocalDateTime {
    val dom = dayOfMonth.coerceIn(1, 28)
    val iv = maxOf(1, interval)
    return when (freq) {
        RecurringFrequency.DAILY -> LocalDate.now().plusDays(iv.toLong()).atStartOfDay()
        RecurringFrequency.WEEKLY -> LocalDate.now().plusWeeks(iv.toLong()).atStartOfDay()
        RecurringFrequency.MONTHLY -> YearMonth.now().atDay(dom).atStartOfDay()
            .let { if (it.isBefore(LocalDateTime.now())) it.plusMonths(iv.toLong()) else it }
        RecurringFrequency.YEARLY -> LocalDate.of(YearMonth.now().year, 1, dom).atStartOfDay()
            .let { if (it.isBefore(LocalDateTime.now())) it.plusYears(iv.toLong()) else it }
    }
}

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周期交易") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("到期的周期交易会自动生成交易记录", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                if (recurringTransactions.isEmpty() && !showAdd) {
                    Text("暂无周期交易", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
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
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(value = rInterval, onValueChange = {
                        if (it.all { c -> c.isDigit() }) rInterval = it
                    }, label = { Text("间隔（每 N 个${unitLabel}执行一次）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
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
                        rFreq, rDayOfMonth.toIntOrNull() ?: 1, rInterval.toIntOrNull() ?: 1
                    )
                    Text(
                        "下次记账: ${previewNext.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                    val nextRun = computeRecurringNextRun(rFreq, dom, interval)
                    viewModel?.addRecurring(RecurringTransaction(
                        categoryId = catId, amount = amt, type = rType, description = rDesc,
                        frequency = rFreq, interval = interval, dayOfMonth = dom, nextRunDate = nextRun
                    ))
                    showAdd = false; rAmount = ""; rDesc = ""; rCatId = null
                }, enabled = rAmount.toDoubleOrNull() != null && rCatId != null) { Text("添加") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ======== App Lock ========
@Composable
fun AppLockSetupDialog(appLockManager: AppLockManager, onDismiss: () -> Unit) {
    val isEnabled = appLockManager.isLockEnabled
    val bioAvailable = appLockManager.canUseBiometric()
    val bioEnabled = appLockManager.isBiometricEnabled
    var showPinInput by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinStep by remember { mutableStateOf(0) } // 0=not entered, 1=enter, 2=confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App 锁") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("启用应用锁", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isEnabled, onCheckedChange = { appLockManager.enableLock(it); if (!it) pinStep = 0 })
                }
                if (isEnabled && bioAvailable) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("指纹/面部解锁", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = bioEnabled, onCheckedChange = { appLockManager.setBiometricEnabled(it) })
                    }
                }
                if (isEnabled && !showPinInput) {
                    TextButton(onClick = { showPinInput = true; pinStep = 1 }) {
                        Text(if (appLockManager.hasPin) "修改密码" else "设置密码")
                    }
                }
                if (isEnabled && showPinInput) {
                    if (pinStep == 1) {
                        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                            label = { Text("输入4-6位数字密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { if (pin.length >= 4) pinStep = 2 }) { Text("下一步") }
                    } else if (pinStep == 2) {
                        OutlinedTextField(value = confirmPin, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                            label = { Text("确认密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = {
                            if (pin == confirmPin) {
                                appLockManager.setPin(pin); showPinInput = false; pinStep = 0; pin = ""; confirmPin = ""
                            }
                        }, enabled = confirmPin.length >= 4) { Text("确认设置") }
                    }
                }
            }
        },
        confirmButton = {},
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
    var yearText by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var isGenerating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("年度账单海报") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("选择年份生成年度账单分享海报", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 4) yearText = it },
                    label = { Text("年份（如 2025）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = {
                        isGenerating = true
                        scope.launch {
                        try {
                            val y = yearText.toIntOrNull()
                            if (y == null || y !in 2000..2099) {
                                Toast.makeText(context, "请输入有效年份（2000-2099）", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val file = posterGenerator.generatePoster(transactions, categories, y)
                                try {
                                    posterGenerator.sharePoster(file)
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
fun ImportDialog(viewModel: TransactionViewModel?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    val scope = rememberCoroutineScope()

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
                onClick = {
                    if (jsonText.isBlank() || viewModel == null) return@Button
                    importing = true
                    scope.launch {
                        try {
                            val importer = DataImportManager()
                            val count = importer.importFromJson(jsonText, viewModel.transactionRepository, viewModel.categoryRepository)
                            importing = false
                            if (count > 0) {
                                Toast.makeText(context, "恢复成功，导入 ${count} 条", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "未导入数据，请检查文件格式", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            importing = false
                            Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = jsonText.isNotBlank() && !importing
            ) { Text(if (importing) "恢复中..." else "恢复") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
