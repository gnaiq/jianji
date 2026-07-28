package com.example.jianji.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 历史交易：列出全部交易（按日期分组、可搜索/筛选），点击进入查看/修改，
 * 滑动删除。复用 HomeScreen 的 SwipeToDeleteItem 与现有编辑弹窗，保持行为一致。
 *
 * §1 P1 搜索过滤增强：在原有「描述/分类名」文本搜索基础上，新增
 * 类型 / 账户 / 金额区间 / 日期区间 多维筛选（复用已加载全量交易，内存过滤）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    accounts: List<Account> = emptyList(),
    tags: List<Tag> = emptyList(),
    transactionTagMap: Map<Long, List<Tag>> = emptyMap(),
    onTransactionClick: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {},
    onOpenRecycle: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // 金额区间用字符串中间态，避免非法输入直接崩；空串=不限制
    var minAmountText by remember { mutableStateOf("") }
    var maxAmountText by remember { mutableStateOf("") }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val effectiveFilters = remember(filters, query) { filters.copy(text = query) }
    val filtered = remember(transactions, query, filters, categoryMap, transactionTagMap) {
        // 分类多选在 applySearchFilters 内处理；标签多选在此做内存交集（OR 语义）
        val base = transactions.applySearchFilters(effectiveFilters, categoryMap)
        if (filters.selectedTags.isEmpty()) base
        else base.filter { tx -> transactionTagMap[tx.id]?.any { it.id in filters.selectedTags } == true }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("搜索描述或分类...") },
            leadingIcon = { Icon(Icons.Default.Search, "搜索") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // 筛选开关 + 清除（复用 SettingsScreen 的 FilterChip 模式）
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showFilters,
                onClick = { showFilters = !showFilters },
                label = { Text(if (showFilters) "收起筛选" else "筛选") }
            )
            if (!effectiveFilters.isEmpty) {
                Text(
                    "${filtered.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    filters = SearchFilters()
                    minAmountText = ""
                    maxAmountText = ""
                }) { Text("清除") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenRecycle) { Icon(Icons.Default.DeleteSweep, "回收站") }
            }
        }

        if (showFilters) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 类型
                Text("类型", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        null to "全部",
                        TransactionType.INCOME to "收入",
                        TransactionType.EXPENSE to "支出",
                        TransactionType.TRANSFER to "转账"
                    ).forEach { (t, label) ->
                        FilterChip(
                            selected = filters.type == t,
                            onClick = { filters = filters.copy(type = t) },
                            label = { Text(label) }
                        )
                    }
                }

                // 账户
                Text("账户", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filters.accountId == null,
                        onClick = { filters = filters.copy(accountId = null) },
                        label = { Text("全部") }
                    )
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = filters.accountId == acc.id,
                            onClick = { filters = filters.copy(accountId = acc.id) },
                            label = { Text(acc.name) }
                        )
                    }
                }

                // 分类多选（§6 搜索多选分类）
                Text("分类", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filters.selectedCategories.isEmpty(),
                        onClick = { filters = filters.copy(selectedCategories = emptySet()) },
                        label = { Text("全部") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = cat.id in filters.selectedCategories,
                            onClick = {
                                filters = filters.copy(
                                    selectedCategories = if (cat.id in filters.selectedCategories)
                                        filters.selectedCategories - cat.id
                                    else filters.selectedCategories + cat.id
                                )
                            },
                            label = { Text("${cat.icon} ${cat.name}") }
                        )
                    }
                }

                // 标签多选（§6）
                Text("标签", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filters.selectedTags.isEmpty(),
                        onClick = { filters = filters.copy(selectedTags = emptySet()) },
                        label = { Text("全部") }
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in filters.selectedTags,
                            onClick = {
                                filters = filters.copy(
                                    selectedTags = if (tag.id in filters.selectedTags)
                                        filters.selectedTags - tag.id
                                    else filters.selectedTags + tag.id
                                )
                            },
                            label = { Text("${tag.icon} ${tag.name}") }
                        )
                    }
                }

                // 金额区间
                Text("金额区间", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minAmountText,
                        onValueChange = {
                            minAmountText = it
                            filters = filters.copy(minAmount = it.toDoubleOrNull())
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("最小") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = maxAmountText,
                        onValueChange = {
                            maxAmountText = it
                            filters = filters.copy(maxAmount = it.toDoubleOrNull())
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("最大") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // 日期区间（含端：结束日 +1 天转为半开区间）
                Text("日期区间", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }) {
                        Text(
                            filters.startDate?.toLocalDate()?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                ?: "起始日"
                        )
                    }
                    OutlinedButton(onClick = { showEndPicker = true }) {
                        Text(
                            filters.endDate?.toLocalDate()?.minusDays(1)
                                ?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "结束日"
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (effectiveFilters.isEmpty) "还没有任何交易记录" else "无匹配交易",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(rows(filtered), key = {
                    when (it) {
                        is LocalDate -> "h_$it"
                        is Transaction -> it.id
                        else -> it.hashCode()
                    }
                }) { row ->
                    when (row) {
                        is LocalDate -> {
                            Text(
                                row.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        is Transaction -> {
                            SwipeToDeleteItem(
                                transaction = row,
                                category = categoryMap[row.categoryId],
                                accountName = row.accountId?.let { accountMap[it]?.name },
                                onClick = { onTransactionClick(row) },
                                onDelete = { onDeleteTransaction(row) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = filters.startDate?.toLocalDate()?.toEpochDay()?.times(86_400_000L)
                ?: LocalDate.now().toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = LocalDate.ofEpochDay(millis / 86_400_000L)
                        filters = filters.copy(startDate = d.atStartOfDay())
                    }
                    showStartPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("取消") } }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (filters.endDate?.toLocalDate()?.minusDays(1))
                ?.toEpochDay()?.times(86_400_000L)
                ?: LocalDate.now().toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d = LocalDate.ofEpochDay(millis / 86_400_000L)
                        // 结束日含端：转为次日 00:00 的半开区间上界
                        filters = filters.copy(endDate = d.plusDays(1).atStartOfDay())
                    }
                    showEndPicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("取消") } }
        ) { DatePicker(state = state) }
    }
}

/**
 * 按日期分组：相邻同日期只显示一次日期头。抽离为顶层函数便于复用与测试。
 */
private fun rows(list: List<Transaction>): List<Any> {
    val result = mutableListOf<Any>()
    var lastDate: LocalDate? = null
    for (tx in list) {
        val d = tx.date.toLocalDate()
        if (d != lastDate) {
            result.add(d)
            lastDate = d
        }
        result.add(tx)
    }
    return result
}
