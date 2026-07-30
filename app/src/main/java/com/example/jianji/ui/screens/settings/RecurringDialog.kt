package com.example.jianji.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import com.example.jianji.ui.viewmodel.SettingsViewModel
import com.example.jianji.utils.*
import java.time.format.DateTimeFormatter

/**
 * 周期交易管理弹窗。
 * 从 SettingsScreen.kt / SettingsDialogs.kt 纯搬移，逻辑与状态提升方式未改动。
 * 下次执行时间计算已抽到 com.example.jianji.utils.computeRecurringNextRun（纯函数，便于测试）。
 */
@Composable
fun RecurringManagementDialog(
    recurringTransactions: List<RecurringTransaction>,
    categories: List<Category>,
    accounts: List<Account>,
    settingsVM: SettingsViewModel?,
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
                            TextButton(onClick = { settingsVM?.deleteRecurring(rt) },
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
                    val nextRun = computeRecurringNextRun(rFreq, dom, interval, dow, rMonthOfYear.toIntOrNull() ?: 1)
                    settingsVM?.addRecurring(RecurringTransaction(
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
