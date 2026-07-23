package com.example.jianji.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jianji.data.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    monthlyIncome: Double = 0.0,
    monthlyExpense: Double = 0.0,
    dailyExpense: Double = 0.0,
    accounts: List<Account> = emptyList(),
    templates: List<QuickTemplate> = emptyList(),
    recurringTransactions: List<RecurringTransaction> = emptyList(),
    searchQuery: String = "",
    isSearching: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: () -> Unit = {},
    onTransactionClick: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {},
    onUseTemplate: (QuickTemplate) -> Unit = {},
    onProcessRecurring: () -> Unit = {}
) {
    val today = LocalDate.now()
    val numberFormat = remember {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }
    }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val defaultAccount = remember(accounts) { accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull() }

    // 搜索模式显示所有匹配交易
    val displayTransactions = remember(transactions, searchQuery, isSearching) {
        if (isSearching && searchQuery.isNotBlank()) {
            transactions.filter { tx ->
                val cat = categoryMap[tx.categoryId]
                val q = searchQuery.trim().lowercase()
                (cat?.name?.lowercase()?.contains(q) == true) ||
                tx.description.lowercase().contains(q)
            }.sortedByDescending { it.date }
        } else {
            transactions.filter { it.date.toLocalDate() == today }.sortedByDescending { it.date }
        }
    }

    val todayTransactions = remember(transactions, today) {
        transactions.filter { it.date.toLocalDate() == today }
    }

    val todayIncome = remember(todayTransactions) {
        todayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    // 预算计算
    val monthYear = YearMonth.now()
    val budgetTotal = monthlyExpense
    // 估算预算（如果没有设定，则显示无限制）
    val budgetProgress = remember(monthlyExpense) { monthExp ->
        // 简单的硬编码默认预算展示（用户设定后会覆盖）
        if (monthExp > 0) monthExp / 5000.0 else 0.0
    }

    val last7Days = remember(today) {
        (0..6).map { daysAgo -> today.minusDays(daysAgo.toLong()) }.reversed()
    }
    val sevenDayStats = remember(transactions, last7Days) {
        last7Days.map { date ->
            val dayIncome = transactions
                .filter { it.date.toLocalDate() == date && it.type == TransactionType.INCOME }
                .sumOf { it.amount }
            val dayExpense = transactions
                .filter { it.date.toLocalDate() == date && it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
            Triple(date, dayIncome, dayExpense)
        }
    }

    if (categories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在加载数据...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        // === 标题 + 搜索按钮 ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("简记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        if (isSearching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "搜索"
                    )
                }
            }
        }

        // === 搜索栏 ===
        if (isSearching) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索描述或分类...") },
                    leadingIcon = { Icon(Icons.Default.Search, "搜索") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // === 本月收支 ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "本月收入", amount = monthlyIncome,
                    color = Color(0xFF4CAF50)
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "本月支出", amount = monthlyExpense,
                    color = Color(0xFFF44336)
                )
                val balance = monthlyIncome - monthlyExpense
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "结余", amount = balance,
                    color = if (balance >= 0) Color(0xFF2196F3) else Color(0xFFFF5722)
                )
            }
        }

        // === 预算进度条 ===
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("月度预算", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "¥${numberFormat.format(monthlyExpense)} / ¥5,000",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (monthlyExpense > 5000) Color(0xFFF44336)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val progress = (monthlyExpense / 5000.0).coerceIn(0.0, 1.0)
                    val barColor = when {
                        progress > 1.0 -> Color(0xFFF44336)
                        progress > 0.8 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                    LinearProgressIndicator(
                        progress = { progress.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.12f),
                        strokeCap = StrokeCap.Round,
                    )
                    if (monthlyExpense > 5000) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已超支 ¥${numberFormat.format(monthlyExpense - 5000)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF44336)
                        )
                    } else if (progress > 0.8) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "剩余 ¥${numberFormat.format(5000 - monthlyExpense)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }
        }

        // === 今日收支 ===
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("今日支出", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text("¥${numberFormat.format(dailyExpense)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(today.format(DateTimeFormatter.ofPattern("MM月dd日 EEEE")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        if (todayIncome > 0) {
                            Text("收入 ¥${numberFormat.format(todayIncome)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // === 快捷模板 ===
        if (templates.isNotEmpty() && !isSearching) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("快捷模板", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(templates.take(8)) { template ->
                                val cat = categoryMap[template.categoryId]
                                Card(
                                    modifier = Modifier.clickable { onUseTemplate(template) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (template.type == TransactionType.EXPENSE)
                                            Color(0xFFF44336).copy(alpha = 0.08f)
                                        else Color(0xFF4CAF50).copy(alpha = 0.08f)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(cat?.icon ?: "📁", style = MaterialTheme.typography.bodyLarge)
                                        Column {
                                            Text(
                                                template.description.ifEmpty { cat?.name ?: "" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                (if (template.type == TransactionType.EXPENSE) "-" else "+") +
                                                    "¥${template.amount.toInt()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (template.type == TransactionType.EXPENSE)
                                                    Color(0xFFF44336) else Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === 最近7天 ===
        if (!isSearching) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("最近 7 天", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sevenDayStats) { (date, income, expense) ->
                                val isToday = date == today
                                Card(
                                    modifier = Modifier.size(72.dp, 90.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isToday)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(date.format(DateTimeFormatter.ofPattern("MM/dd")),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text(
                                            text = if (isToday) "今天" else date.format(DateTimeFormatter.ofPattern("EEE")),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isToday) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        if (expense > 0) {
                                            Text("¥${expense.toInt()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF44336),
                                                textAlign = TextAlign.Center)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // === 交易列表 ===
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isSearching) "搜索结果" else "今日交易",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("${displayTransactions.size} 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        if (displayTransactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        if (isSearching && searchQuery.isNotBlank()) "无匹配交易"
                        else "今天还没有交易记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(displayTransactions, key = { it.id }) { transaction ->
                SwipeToDeleteItem(
                    transaction = transaction,
                    category = categoryMap[transaction.categoryId],
                    accountName = transaction.accountId?.let { accountMap[it]?.name },
                    onClick = { onTransactionClick(transaction) },
                    onDelete = { onDeleteTransaction(transaction) }
                )
            }
        }

        item { Spacer(Modifier.size(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteItem(
    transaction: Transaction,
    category: Category?,
    accountName: String? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                    else -> Color.Transparent
                }
            )
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    .background(color).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Default.Delete, "删除", tint = Color.White)
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        TransactionItemCard(
            transaction = transaction, category = category,
            accountName = accountName, onClick = onClick, onDelete = onDelete
        )
    }
}

@Composable
fun SummaryCard(modifier: Modifier = Modifier, title: String, amount: Double, color: Color) {
    val nf = remember {
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
            Text("¥${nf.format(amount)}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TransactionItemCard(
    transaction: Transaction,
    category: Category? = null,
    accountName: String? = null,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(category?.icon ?: "📁", style = MaterialTheme.typography.bodyLarge)
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(category?.name ?: "未分类", style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold)
                        if (accountName != null) {
                            Text(accountName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    if (transaction.description.isNotEmpty()) {
                        Text(transaction.description, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (transaction.type == TransactionType.INCOME) "+¥${formatAmount(transaction.amount)}"
                    else "-¥${formatAmount(transaction.amount)}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    color = if (transaction.type == TransactionType.INCOME) Color(0xFF4CAF50) else Color(0xFFF44336))
                Text(transaction.date.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}

fun formatAmount(amount: Double): String {
    return NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(amount)
}
