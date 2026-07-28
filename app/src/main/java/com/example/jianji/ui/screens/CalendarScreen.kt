package com.example.jianji.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    onBack: () -> Unit = {}
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val txByDay = remember(transactions, month) {
        transactions
            .filter { it.date.toLocalDate().year == month.year && it.date.toLocalDate().month == month.month }
            .groupBy { it.date.toLocalDate() }
    }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val sheetState = rememberModalBottomSheetState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Text(
                month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "上月") }
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "下月") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(8.dp))

        val firstDay = month.atDay(1)
        val leading = (firstDay.dayOfWeek.value % 7) // 周日=0
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((leading + daysInMonth + 6) / 7) * 7

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(totalCells) { index ->
                val day = index - leading + 1
                if (day in 1..daysInMonth) {
                    val date = month.atDay(day)
                    val dayTx = txByDay[date] ?: emptyList()
                    val expense = dayTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents } / 100.0
                    val income = dayTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents } / 100.0
                    val isToday = date == LocalDate.now()
                    Column(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { selectedDay = date }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
                        if (dayTx.isNotEmpty()) {
                            if (expense > 0) Text(
                                "-%.0f".format(expense), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (income > 0) Text(
                                "+%.0f".format(income), style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                } else {
                    Box(Modifier.aspectRatio(1f))
                }
            }
        }
    }

    if (selectedDay != null) {
        val day = selectedDay!!
        val dayTx = txByDay[day] ?: emptyList()
        ModalBottomSheet(onDismissRequest = { selectedDay = null }, sheetState = sheetState) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(day.format(DateTimeFormatter.ofPattern("M 月 d 日")),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (dayTx.isEmpty()) {
                    Text("当日无交易", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                } else {
                    dayTx.forEach { tx ->
                        val cat = categoryMap[tx.categoryId]
                        val isExpense = tx.type == TransactionType.EXPENSE
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat?.icon ?: "💰", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cat?.name ?: "未分类", style = MaterialTheme.typography.bodyMedium)
                                if (tx.description.isNotBlank())
                                    Text(tx.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Text(
                                (if (isExpense) "-¥" else "+¥") + String.format("%.2f", tx.amountCents / 100.0),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
