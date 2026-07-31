package com.example.jianji.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import com.example.jianji.ui.components.SummaryCard
import com.example.jianji.ui.components.SwipeToDeleteItem
import com.example.jianji.ui.components.formatAmount
import com.example.jianji.ui.theme.AppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    monthlyIncome: Double = 0.0,
    monthlyExpense: Double = 0.0,
    dailyExpense: Double = 0.0,
    monthlyBudget: Double = 0.0,
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
    onProcessRecurring: () -> Unit = {},
    transactionTagMap: Map<Long, List<Tag>> = emptyMap(),
    onOpenCalendar: () -> Unit = {}
) {
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val defaultAccount = remember(accounts) { accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull() }

    // 搜索模式显示所有匹配交易
    val displayTransactions = remember(transactions, searchQuery, isSearching, selectedDate) {
        if (isSearching && searchQuery.isNotBlank()) {
            transactions.filter { tx ->
                val cat = categoryMap[tx.categoryId]
                val q = searchQuery.trim().lowercase()
                (cat?.name?.lowercase()?.contains(q) == true) ||
                tx.description.lowercase().contains(q)
            }.sortedByDescending { it.date }
        } else {
            transactions.filter { it.date.toLocalDate() == selectedDate }.sortedByDescending { it.date }
        }
    }

    val todayTransactions = remember(transactions, today) {
        transactions.filter { it.date.toLocalDate() == today }
    }

    val todayIncome = remember(todayTransactions) {
        todayTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents / 100.0 }
    }

    // 预算计算（使用真实设置的月度预算；未设置则为 0，表示不限）
    val budgetProgress = remember(monthlyExpense, monthlyBudget) {
        if (monthlyBudget > 0) (monthlyExpense / monthlyBudget).coerceIn(0.0, 1.0) else 0.0
    }

    val last7Days = remember(today) {
        (0..6).map { daysAgo -> today.minusDays(daysAgo.toLong()) }.reversed()
    }
    val sevenDayStats = remember(transactions, last7Days) {
        last7Days.map { date ->
            val dayIncome = transactions
                .filter { it.date.toLocalDate() == date && it.type == TransactionType.INCOME }
                .sumOf { it.amountCents / 100.0 }
            val dayExpense = transactions
                .filter { it.date.toLocalDate() == date && it.type == TransactionType.EXPENSE }
                .sumOf { it.amountCents / 100.0 }
            Triple(date, dayIncome, dayExpense)
        }
    }

    // 首次安装时应用启动异步 seed 默认分类/账户，首帧 categories 可能仍为空。
    // 不再以 return 永久阻塞整屏，仅以一条轻量提示占位；seed 完成后 categories 非空，
    // 本 Composable 自动重组并正常渲染（避免“正在加载”永久卡死，修复 DEF-001 UI 端）。
    val isInitializing = categories.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        // === 首次初始化占位（仅在 categories 尚未 seed 完成时显示） ===
        if (isInitializing) {
            item {
                Text(
                    "正在初始化默认数据...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                    color = AppColors.IncomeGreen
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "本月支出", amount = monthlyExpense,
                    color = AppColors.ExpenseRed
                )
                val balance = monthlyIncome - monthlyExpense
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "结余", amount = balance,
                    color = if (balance >= 0) AppColors.BalanceBlue else AppColors.BalanceNegative
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
                        if (monthlyBudget > 0) {
                            Text(
                                "¥${formatAmount(monthlyExpense)} / ¥${formatAmount(monthlyBudget)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (monthlyExpense > monthlyBudget) AppColors.ExpenseRed
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                "未设置预算",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    if (monthlyBudget > 0) {
                        Spacer(Modifier.height(6.dp))
                        val progress = (monthlyExpense / monthlyBudget).coerceIn(0.0, 1.0)
                        val barColor = when {
                            progress > 1.0 -> AppColors.BudgetOverrun
                            progress > 0.8 -> AppColors.BudgetWarning
                            else -> AppColors.BudgetSafe
                        }
                        LinearProgressIndicator(
                            progress = { progress.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = barColor.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round,
                        )
                        if (monthlyExpense > monthlyBudget) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "已超支 ¥${formatAmount(monthlyExpense - monthlyBudget)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.ExpenseRed
                            )
                        } else if (progress > 0.8) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "剩余 ¥${formatAmount(monthlyBudget - monthlyExpense)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.BudgetWarning
                            )
                        }
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
                        Text("¥${formatAmount(dailyExpense)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(today.format(DateTimeFormatter.ofPattern("MM月dd日 EEEE")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        if (todayIncome > 0) {
                            Text("收入 ¥${formatAmount(todayIncome)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.IncomeGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // === 快捷模板 ===
        if (templates.isNotEmpty() && !isSearching) {
            item {
                var templateExpanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { templateExpanded = !templateExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("快捷模板", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = if (templateExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (templateExpanded) "收起" else "展开"
                            )
                        }
                        if (templateExpanded) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(templates.take(8)) { template ->
                                    val cat = categoryMap[template.categoryId]
                                    Card(
                                        modifier = Modifier.clickable { onUseTemplate(template) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (template.type == TransactionType.EXPENSE)
                                                AppColors.ExpenseRed.copy(alpha = 0.08f)
                                            else AppColors.IncomeGreen.copy(alpha = 0.08f)
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
                                                        "¥${"%.2f".format(template.amountCents / 100.0)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (template.type == TransactionType.EXPENSE)
                                                        AppColors.ExpenseRed else AppColors.IncomeGreen
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("最近 7 天", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onOpenCalendar) {
                                Icon(Icons.Default.DateRange, "日历视图", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("月历", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sevenDayStats) { (date, income, expense) ->
                                val isToday = date == today
                                val isSelected = date == selectedDate
                                Card(
                                    modifier = Modifier.size(72.dp, 90.dp).clickable { selectedDate = date },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
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
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        if (expense > 0) {
                                            Text("¥${formatAmount(expense)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AppColors.ExpenseRed,
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
                    if (isSearching) "搜索结果"
                    else if (selectedDate == today) "今日交易"
                    else "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))} 交易",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isSearching && selectedDate != today) {
                        TextButton(onClick = { selectedDate = today }) {
                            Text("回到今天", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("${displayTransactions.size} 笔",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }

        if (displayTransactions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        if (isSearching && searchQuery.isNotBlank()) "无匹配交易"
                        else if (selectedDate == today) "今天还没有交易记录"
                        else "${selectedDate.format(DateTimeFormatter.ofPattern("M月d日"))} 还没有交易记录",
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
                    toAccountName = transaction.toAccountId?.let { accountMap[it]?.name },
                    tags = transactionTagMap[transaction.id] ?: emptyList(),
                    onClick = { onTransactionClick(transaction) },
                    onDelete = { onDeleteTransaction(transaction) }
                )
            }
        }

        item { Spacer(Modifier.size(80.dp)) }
    }
}