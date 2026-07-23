package com.example.jianji.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            2 -> YearlyStatistics(transactions)
        }
    }
}

private fun getWeekRange(): Pair<LocalDateTime, LocalDateTime> {
    val today = LocalDate.now()
    val startOfWeek = today.with(DayOfWeek.MONDAY)
    val endOfWeek = today.with(DayOfWeek.SUNDAY)
    return Pair(startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX))
}

private fun getMonthRange(): Pair<LocalDateTime, LocalDateTime> {
    val today = LocalDate.now()
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
    val name: String,
    val amount: Double,
    val percentage: Float
)

private fun calculateCategoryStats(
    transactions: List<Transaction>,
    categories: List<Category>,
    start: LocalDateTime,
    end: LocalDateTime
): Pair<Double, List<CategoryStat>> {
    val filtered = transactions.filter {
        it.type == TransactionType.EXPENSE && !it.date.isBefore(start) && !it.date.isAfter(end)
    }
    val totalExpense = filtered.sumOf { it.amount }

    val byCategory = filtered.groupBy { it.categoryId }
        .map { (categoryId, txns) ->
            val category = categories.find { c -> c.id == categoryId }
            val amount = txns.sumOf { it.amount }
            CategoryStat(
                name = category?.name ?: "未分类",
                amount = amount,
                percentage = if (totalExpense > 0) (amount / totalExpense * 100).toFloat() else 0f
            )
        }
        .sortedByDescending { it.amount }

    return Pair(totalExpense, byCategory)
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
    val (totalExpense, categoryStats) = calculateCategoryStats(transactions, categories, start, end)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("本周统计", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "收入",
                amount = "¥${String.format("%,.2f", income)}",
                color = IncomeGreen,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "支出",
                amount = "¥${String.format("%,.2f", totalExpense)}",
                color = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
        }

        Text("分类统计", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

        if (categoryStats.isEmpty()) {
            Text(
                text = "本周暂无支出记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryStats) { stat ->
                    CategoryStatItem(
                        category = stat.name,
                        amount = "¥${String.format("%,.2f", stat.amount)}",
                        percentage = stat.percentage
                    )
                }
            }
        }
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
    val (totalExpense, categoryStats) = calculateCategoryStats(transactions, categories, start, end)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("本月统计", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "收入",
                amount = "¥${String.format("%,.2f", income)}",
                color = IncomeGreen,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "支出",
                amount = "¥${String.format("%,.2f", totalExpense)}",
                color = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
        }

        Text("分类统计", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

        if (categoryStats.isEmpty()) {
            Text(
                text = "本月暂无支出记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryStats) { stat ->
                    CategoryStatItem(
                        category = stat.name,
                        amount = "¥${String.format("%,.2f", stat.amount)}",
                        percentage = stat.percentage
                    )
                }
            }
        }
    }
}

@Composable
fun YearlyStatistics(
    transactions: List<Transaction> = emptyList()
) {
    val (start, end) = getYearRange()
    val yearTransactions = transactions.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    val income = yearTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = yearTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    // Group by month
    val monthlyData = (1..12).map { month ->
        val monthTxns = yearTransactions.filter { it.date.monthValue == month }
        val monthExpense = monthTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        Pair(month, monthExpense)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("年度统计", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "收入",
                amount = "¥${String.format("%,.2f", income)}",
                color = IncomeGreen,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "支出",
                amount = "¥${String.format("%,.2f", expense)}",
                color = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
        }

        Text("月度趋势", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(monthlyData) { (month, amount) ->
                MonthTrendItem(
                    month = "${LocalDate.now().year}-${String.format("%02d", month)}",
                    amount = "¥${String.format("%,.2f", amount)}"
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    amount: String,
    color: androidx.compose.ui.graphics.Color,
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CategoryStatItem(
    category: String,
    amount: String,
    percentage: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = ExpenseRed
            )
            Text(
                text = "${String.format("%.1f", percentage)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun MonthTrendItem(
    month: String,
    amount: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
        }
    }
}
