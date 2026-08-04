package com.example.jianji.ui.screens.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import com.example.jianji.ui.viewmodel.AccountViewModel
import com.example.jianji.ui.viewmodel.BudgetViewModel
import com.example.jianji.ui.viewmodel.SettingsViewModel
import com.example.jianji.utils.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 设置页各功能弹窗（预算/账户/模板/周期交易/年度海报）。
 * 从 SettingsScreen.kt 纯搬移，函数签名、逻辑、状态提升方式均未改动。
 */

// ======== Budget Dialog ========
@Composable
fun BudgetSettingsDialog(budgetVM: BudgetViewModel?, onDismiss: () -> Unit) {
    val year = YearMonth.now().year
    val month = YearMonth.now().monthValue
    // 回显当前月度预算：保存完整 Budget 实体以支持删除（需 id）
    var currentBudgetEntity by remember { mutableStateOf<Budget?>(null) }
    val currentBudget = currentBudgetEntity?.amountCents?.toDouble()?.div(100.0) ?: 0.0
    LaunchedEffect(budgetVM) {
        budgetVM?.let { vm ->
            val ym = YearMonth.of(year, month)
            // 通过 Repository 直接读取完整 Budget 实体
            val budget = vm.getMonthlyBudgetEntity(ym)
            currentBudgetEntity = budget
        }
    }
    var budgetAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(currentBudget) {
        if (currentBudget > 0 && budgetAmount.isEmpty()) budgetAmount = "%.2f".format(currentBudget)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预算设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("设定 ${year}年${month}月 月度预算（当前：¥${"%.2f".format(currentBudget)}）", style = MaterialTheme.typography.bodyMedium)
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (currentBudgetEntity != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    budgetVM?.deleteBudget(currentBudgetEntity!!)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("删除预算") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Button(onClick = {
                            val amt = budgetAmount.toDoubleOrNull() ?: return@Button
                            scope.launch {
                                budgetVM?.setBudget(Budget(
                                    amountCents = Math.round(amt * 100), period = BudgetPeriod.MONTHLY,
                                    year = year, month = month
                                ))
                            }
                            onDismiss()
                        }) { Text("保存") }
                    }
                }
            },
            dismissButton = {}
    )
}

// ======== Account Dialog ========
@Composable
fun AccountManagementDialog(accounts: List<Account>, accountVM: AccountViewModel?, onDismiss: () -> Unit, accountBalances: Map<Long, Double> = emptyMap()) {
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
                                    TextButton(onClick = { accountVM?.setDefaultAccount(acc.id) }) { Text("默认") }
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
                    if (newName.isNotBlank()) { accountVM?.addAccount(newName, newIcon); showAdd = false; newName = "" }
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
                        accountVM?.deleteAccountCascade(pendingDelete!!)
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
    settingsVM: SettingsViewModel?,
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
                                    Text("${if (t.type == TransactionType.EXPENSE) "-" else "+"}¥${"%.2f".format(t.amountCents / 100.0)} · 使用${t.useCount}次",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            TextButton(onClick = { settingsVM?.deleteTemplate(t) },
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
                    settingsVM?.addTemplate(QuickTemplate(categoryId = catId, amountCents = Math.round(amt * 100), type = tmpType, description = tmpDesc))
                    showAdd = false; tmpAmount = ""; tmpDesc = ""; tmpCatId = null
                }, enabled = tmpAmount.toDoubleOrNull() != null && tmpCatId != null) { Text("添加") }
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

// ======== 备份加密口令设置（P6-1 收尾）========
/**
 * 设置/清除备份加密口令。
 * - 设置时二次确认输入，避免误输入（口令无法找回，见 backup-encryption-design.md §5）。
 * - 清除后备份恢复为明文（向下兼容旧备份恢复）。
 */
@Composable
fun BackupPassphraseDialog(
    initialSet: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onCleared: () -> Unit
) {
    val context = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialSet) "修改/清除备份加密口令" else "设置备份加密口令") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "口令一旦遗忘，对应加密备份将永久无法恢复。请务必牢记口令。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it; error = null },
                    label = { Text("加密口令") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it; error = null },
                    label = { Text("确认口令") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pass.length < 4 -> error = "口令至少 4 位"
                    pass != confirm -> error = "两次输入不一致"
                    else -> {
                        AppPrefs.setBackupPassphrase(context, pass)
                        Toast.makeText(context, "备份加密口令已设置", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                }
            }) { Text("设置口令") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialSet) {
                    TextButton(onClick = {
                        AppPrefs.setBackupPassphrase(context, "")
                        Toast.makeText(context, "已清除口令，备份将恢复明文", Toast.LENGTH_SHORT).show()
                        onCleared()
                    }) { Text("清除口令") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
