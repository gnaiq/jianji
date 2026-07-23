package com.example.jianji.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import com.example.jianji.ui.theme.ExpenseRed
import com.example.jianji.ui.theme.IncomeGreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun StatisticsScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "统计分析",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("周统计") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("月统计") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("年统计") }
            )
        }

        when (selectedTab) {
            0 -> WeeklyStatistics(transactions, categories)
            1 -> MonthlyStatistics(transactions, categories)
            2 -> YearlyStatistics(transactions, categories)
        }
    }
}

private fun getWeekRange(): Pair<LocalDateTime, LocalDateTime> {
    val today = LocalDate.now()
    val startOfWeek = today.with(DayOfWeek.MONDAY)
    val endOfWeek = today.with(DayOfWeek.SUNDAY)
    return Pair(startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX))
}

private fun getMonthRange(monthOffset: Int = 0): Pair<LocalDateTime, LocalDateTime> {
    val today = LocalDate.now().minusMonths(monthOffset.toLong())
    val startOfMonth = today.with(TemporalAdjusters.firstDayOfMonth())
    val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
    return Pair(startOfMonth.atStartOfDay(), endOfMonth.atTime(LocalTime.MAX))
}

private fun getYearRange(): Pair<LocalDateTime, LocalDateTime> {
    val today = LocalDate.now()
    val startOfYear = today.with(TemporalAdjusters.firstDayOfYear())
    val endOfYear = today.with(TemporalAdjusters.lastDayOfYear())
    return Pair(startOfYear.atStartOfDay(), endOfYear.atTime(LocalTime.MAX))
}

private data class CategoryStat(
    val categoryId: Long,
    val name: String,
    val amount: Double,
    val percentage: Float
)

private fun calculateCategoryStats(
    transactions: List<Transaction>,
    categories: List<Category>,
    start: LocalDateTime,
    end: LocalDateTime,
    type: TransactionType = TransactionType.EXPENSE
): Pair<Double, List<CategoryStat>> {
    val filtered = transactions.filter {
        it.type == type && !it.date.isBefore(start) && !it.date.isAfter(end)
    }
    val totalAmount = filtered.sumOf { it.amount }

    val byCategory = filtered.groupBy { it.categoryId }
        .map { (categoryId, txns) ->
            val category = categories.find { c -> c.id == categoryId }
            val amount = txns.sumOf { it.amount }
            CategoryStat(
                categoryId = categoryId,
                name = category?.name ?: "未分类",
                amount = amount,
                percentage = if (totalAmount > 0) (amount / totalAmount * 100).toFloat() else 0f
            )
        }
        .sortedByDescending { it.amount }

    return Pair(totalAmount, byCategory)
}

/** 分类交易明细对话框 */
@Composable
fun TransactionDetailDialog(
    title: String,
    transactions: List<Transaction>,
    categories: Map<Long, Category>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (transactions.isEmpty()) {
                Text("暂无交易记录")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(transactions.sortedByDescending { it.date }) { transaction ->
                        val cat = categories[transaction.categoryId]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cat?.icon + " " + (cat?.name ?: "未分类"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = transaction.date.format(
                                            DateTimeFormatter.ofPattern("MM-dd HH:mm")
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    if (!transaction.description.isNullOrEmpty()) {
                                        Text(
                                            text = transaction.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Text(
                                    text = if (transaction.type == TransactionType.INCOME)
                                        "+¥${formatAmount(transaction.amount)}"
                                    else "-¥${formatAmount(transaction.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (transaction.type == TransactionType.INCOME)
                                        IncomeGreen else ExpenseRed
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun WeeklyStatistics(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList()
) {
    val (start, end) = getWeekRange()
    val income = transactions.filter {
        it.type == TransactionType.INCOME && !it.date.isBefore(start) && !it.date.isAfter(end)
    }.sumOf { it.amount }
    val (totalExpense, expenseStats) = calculateCategoryStats(transactions, categories, start, end)
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var detailCategory by remember { mutableStateOf<CategoryStat?>(null) }

    val detailTransactions = remember(detailCategory, transactions) {
        val cat = detailCategory ?: return@remember emptyList()
        transactions.filter {
            it.categoryId == cat.categoryId && !it.date.isBefore(start) && !it.date.isAfter(end)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("本周统计", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("收入", "¥${formatAmount(income)}", IncomeGreen, Modifier.weight(1f))
            StatCard("支出", "¥${formatAmount(totalExpense)}", ExpenseRed, Modifier.weight(1f))
        }

        Text("支出分类（点击查看明细）", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

        if (expenseStats.isEmpty()) {
            Text("本周暂无支出记录", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenseStats) { stat ->
                    CategoryStatItem(
                        category = stat.name,
                        amount = "¥${formatAmount(stat.amount)}",
                        percentage = stat.percentage,
                        onClick = { detailCategory = stat }
                    )
                }
            }
        }
    }

    if (detailCategory != null) {
        TransactionDetailDialog(
            title = "${detailCategory!!.name} - 本周明细",
            transactions = detailTransactions,
            categories = categoryMap,
            onDismiss = { detailCategory = null }
        )
    }
}

@Composable
fun MonthlyStatistics(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList()
) {
    val (start, end) = getMonthRange()
    val income = transactions.filter {
        it.type == TransactionType.INCOME && !it.date.isBefore(start) && !it.date.isAfter(end)
    }.sumOf { it.amount }
    val (totalExpense, expenseStats) = calculateCategoryStats(transactions, categories, start, end)
    val (_, incomeStats) = calculateCategoryStats(transactions, categories, start, end, TransactionType.INCOME)
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var detailCategory by remember { mutableStateOf<CategoryStat?>(null) }

    val detailTransactions = remember(detailCategory, transactions) {
        val cat = detailCategory ?: return@remember emptyList()
        transactions.filter {
            it.categoryId == cat.categoryId && !it.date.isBefore(start) && !it.date.isAfter(end)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("本月统计（${start.format(DateTimeFormatter.ofPattern("yyyy年MM月"))}）",
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("收入", "¥${formatAmount(income)}", IncomeGreen, Modifier.weight(1f))
            StatCard("支出", "¥${formatAmount(totalExpense)}", ExpenseRed, Modifier.weight(1f))
        }

        // 支出分类统计
        Text("支出分类（点击查看明细）", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        if (expenseStats.isEmpty()) {
            Text("本月暂无支出记录", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenseStats) { stat ->
                    CategoryStatItem(
                        category = stat.name,
                        amount = "¥${formatAmount(stat.amount)}",
                        percentage = stat.percentage,
                        onClick = { detailCategory = stat }
                    )
                }
            }
        }

        // 收入分类统计
        if (incomeStats.isNotEmpty()) {
            Text("收入分类", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(incomeStats) { stat ->
                    CategoryStatItem(
                        category = stat.name,
                        amount = "¥${formatAmount(stat.amount)}",
                        percentage = stat.percentage,
                        color = IncomeGreen,
                        onClick = { detailCategory = stat }
                    )
                }
            }
        }
    }

    if (detailCategory != null) {
        TransactionDetailDialog(
            title = "${detailCategory!!.name} - ${start.format(DateTimeFormatter.ofPattern("yyyy年MM月"))}明细",
            transactions = detailTransactions,
            categories = categoryMap,
            onDismiss = { detailCategory = null }
        )
    }
}

@Composable
fun YearlyStatistics(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList()
) {
    val (start, end) = getYearRange()
    val yearTransactions = transactions.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    val income = yearTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = yearTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    // 年度分类汇总
    val (_, yearCategoryStats) = calculateCategoryStats(transactions, categories, start, end)

    // 按月份汇总
    val monthlyStats = (1..12).map { month ->
        val monthStart = LocalDate.of(start.year, month, 1).atStartOfDay()
        val monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59)
        val monthTxns = yearTransactions.filter { !it.date.isBefore(monthStart) && !it.date.isAfter(monthEnd) }
        val monthIncome = monthTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val monthExpense = monthTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        Triple(month, monthIncome, monthExpense)
    }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    var detailCategory by remember { mutableStateOf<CategoryStat?>(null) }

    // 点击月度时的分类明细
    val monthCategoryStats = remember(selectedMonth, transactions) {
        val m = selectedMonth ?: return@remember emptyList()
        val ms = LocalDate.of(start.year, m, 1).atStartOfDay()
        val me = ms.with(TemporalAdjusters.lastDayOfMonth()).withHour(23).withMinute(59).withSecond(59)
        calculateCategoryStats(transactions, categories, ms, me).second
    }

    val detailTransactions = remember(detailCategory, transactions) {
        val cat = detailCategory ?: return@remember emptyList()
        transactions.filter {
            it.categoryId == cat.categoryId && !it.date.isBefore(start) && !it.date.isAfter(end)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("年度统计（${start.year}年）",
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("年度收入", "¥${formatAmount(income)}", IncomeGreen, Modifier.weight(1f))
            StatCard("年度支出", "¥${formatAmount(expense)}", ExpenseRed, Modifier.weight(1f))
        }

        // 月度趋势（点击查看该月分类明细）
        Text("月度趋势（点击查看分类明细）", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(monthlyStats) { (month, mIncome, mExpense) ->
                val isSelected = selectedMonth == month
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedMonth = if (isSelected) null else month
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${month}月",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "收 ¥${formatAmount(mIncome)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IncomeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "支 ¥${formatAmount(mExpense)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ExpenseRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        // 该月的分类明细
                        if (isSelected && monthCategoryStats.isNotEmpty()) {
                            monthCategoryStats.forEach { stat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stat.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "¥${formatAmount(stat.amount)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ExpenseRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${String.format("%.1f", stat.percentage)}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 年度分类汇总
        if (yearCategoryStats.isNotEmpty()) {
            Text("年度分类汇总（点击查看明细）",
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(yearCategoryStats) { stat ->
                    CategoryStatItem(
                        category = stat.name,
                        amount = "¥${formatAmount(stat.amount)}",
                        percentage = stat.percentage,
                        onClick = { detailCategory = stat }
                    )
                }
            }
        }
    }

    if (detailCategory != null) {
        TransactionDetailDialog(
            title = "${detailCategory!!.name} - ${start.year}年明细",
            transactions = detailTransactions,
            categories = categoryMap,
            onDismiss = { detailCategory = null }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface)
            Text(amount, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CategoryStatItem(
    category: String,
    amount: String,
    percentage: Float,
    color: Color = ExpenseRed,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                Text(category, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${String.format("%.1f", percentage)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text("点击查看明细 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            }
        }
    }
}
