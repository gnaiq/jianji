package com.example.jianji.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
) {
    var selectedTab by remember { mutableStateOf(0) }
    var weekOffset by remember { mutableStateOf(0) }
    var monthOffset by remember { mutableStateOf(0) }
    var yearOffset by remember { mutableStateOf(0) }
    // 手动选日期作为统计基准日（默认今天），不影响原翻页逻辑
    var baseDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val tabs = listOf("周统计", "月统计", "年统计")

    // category name lookup
    val categoryNames = remember(categories) {
        categories.associate { it.id to it.name }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> WeekStatistics(
                transactions = transactions,
                categoryNames = categoryNames,
                baseDate = baseDate,
                weekOffset = weekOffset,
                onPrevWeek = { weekOffset-- },
                onNextWeek = { if (weekOffset < 0) weekOffset++ else weekOffset = 0 },
                onPickDate = { showDatePicker = true }
            )
            1 -> MonthStatistics(
                transactions = transactions,
                categoryNames = categoryNames,
                baseDate = baseDate,
                monthOffset = monthOffset,
                onPrevMonth = { monthOffset-- },
                onNextMonth = { if (monthOffset < 0) monthOffset++ else monthOffset = 0 },
                onPickDate = { showDatePicker = true }
            )
            2 -> YearStatistics(
                transactions = transactions,
                categoryNames = categoryNames,
                baseDate = baseDate,
                yearOffset = yearOffset,
                onPrevYear = { yearOffset-- },
                onNextYear = { if (yearOffset < 0) yearOffset++ else yearOffset = 0 },
                onPickDate = { showDatePicker = true }
            )
        }

        if (showDatePicker) {
            when (selectedTab) {
                1 -> MonthPickerDialog(
                    initial = baseDate,
                    onConfirm = {
                        baseDate = it
                        weekOffset = 0; monthOffset = 0; yearOffset = 0
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
                2 -> YearPickerDialog(
                    initial = baseDate,
                    onConfirm = {
                        baseDate = it
                        weekOffset = 0; monthOffset = 0; yearOffset = 0
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
                else -> {
                    val pickerState = rememberDatePickerState(
                        initialSelectedDateMillis = baseDate.toEpochDay() * 86_400_000L
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                pickerState.selectedDateMillis?.let { millis ->
                                    baseDate = LocalDate.ofEpochDay(millis / 86_400_000L)
                                    weekOffset = 0; monthOffset = 0; yearOffset = 0
                                }
                                showDatePicker = false
                            }) { Text("确定") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        }
                    ) {
                        DatePicker(state = pickerState)
                    }
                }
            }
        }
    }
}

// ─── 月 / 年 选择器（不出现日 / 月+日） ─────────────────────

@Composable
private fun MonthPickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var displayYear by remember { mutableStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择月份") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { displayYear-- }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一年")
                    }
                    Text("${displayYear}年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { displayYear++ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一年")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    items((1..12).toList()) { m ->
                        val selected = displayYear == initial.year && m == initial.monthValue
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(LocalDate.of(displayYear, m, 1)) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                            ) {
                                Text("${m}月", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun YearPickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val years = remember(initial) { ((initial.year - 10)..(initial.year + 1)).toList().reversed() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年份") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(years) { y ->
                    val selected = y == initial.year
                    TextButton(
                        onClick = { onConfirm(LocalDate.of(y, 1, 1)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${y}年",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// 选中数据点后展示的时段费用
private data class ChartPoint(val label: String, val expense: Float, val income: Float)

// ─── Trend Line Chart (MPAndroidChart) ────────────────────

@Composable
private fun TrendLineChart(
    title: String,
    labels: List<String>,
    expense: List<Float>,
    income: List<Float> = emptyList()
) {
    if (labels.isEmpty()) return
    if (expense.none { it > 0f } && income.none { it > 0f }) return

    var expanded by remember { mutableStateOf(false) }
    val selectedPoint = remember { mutableStateOf<ChartPoint?>(null) }

    val valueFormatter = remember {
        object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String =
                if (value >= 10000f) "¥%.0f".format(value) else "¥%.1f".format(value)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                AndroidView(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                factory = { ctx ->
                    LineChart(ctx).apply {
                        description = Description().apply { isEnabled = false }
                        setScaleEnabled(false)
                        setTouchEnabled(true)
                        setPinchZoom(false)
                        legend.isEnabled = true
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)
                        xAxis.granularity = 1f
                        xAxis.labelRotationAngle = -45f
                        xAxis.setLabelCount(6, false)
                        axisRight.isEnabled = false
                        axisLeft.setDrawGridLines(true)
                        axisLeft.axisMinimum = 0f
                    }
                },
                update = { chart ->
                    val dataSets = mutableListOf<ILineDataSet>()
                    if (expense.any { it > 0f }) {
                        val ds = LineDataSet(
                            expense.mapIndexed { i, v -> Entry(i.toFloat(), v) }, "支出"
                        ).apply {
                            color = android.graphics.Color.parseColor("#F44336")
                            setCircleColor(android.graphics.Color.parseColor("#F44336"))
                            lineWidth = 2f
                            circleRadius = 3f
                            setDrawCircles(false)
                            setDrawValues(false)
                            setValueTextSize(9f)
                            setValueTextColor(android.graphics.Color.parseColor("#B71C1C"))
                            setValueFormatter(valueFormatter)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        dataSets.add(ds)
                    }
                    if (income.any { it > 0f }) {
                        val ds = LineDataSet(
                            income.mapIndexed { i, v -> Entry(i.toFloat(), v) }, "收入"
                        ).apply {
                            color = android.graphics.Color.parseColor("#4CAF50")
                            setCircleColor(android.graphics.Color.parseColor("#4CAF50"))
                            lineWidth = 2f
                            circleRadius = 3f
                            setDrawCircles(false)
                            setDrawValues(false)
                            setValueTextSize(9f)
                            setValueTextColor(android.graphics.Color.parseColor("#1B5E20"))
                            setValueFormatter(valueFormatter)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                        }
                        dataSets.add(ds)
                    }
                    chart.data = LineData(dataSets)
                    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            val idx = e?.x?.toInt() ?: return
                            if (idx in labels.indices) {
                                selectedPoint.value = ChartPoint(
                                    labels[idx],
                                    expense.getOrElse(idx) { 0f },
                                    income.getOrElse(idx) { 0f }
                                )
                            }
                        }
                        override fun onNothingSelected() {
                            selectedPoint.value = null
                        }
                    })
                    chart.invalidate()
                }
            )
                val chartInfo = selectedPoint.value
                Text(
                    text = if (chartInfo != null) {
                        if (chartInfo.income > 0f) {
                            "%s · 支出 ¥%.2f · 收入 ¥%.2f".format(chartInfo.label, chartInfo.expense, chartInfo.income)
                        } else {
                            "%s · 支出 ¥%.2f".format(chartInfo.label, chartInfo.expense)
                        }
                    } else {
                        "轻点折线上的数据点，查看该时段费用"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
        }
    }
    }
}

// ─── Week ──────────────────────────────────────────────────

@Composable
private fun WeekStatistics(
    transactions: List<Transaction>,
    categoryNames: Map<Long, String>,
    baseDate: LocalDate,
    weekOffset: Int,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onPickDate: () -> Unit
) {
    val range = getWeekRange(baseDate, weekOffset)
    val filtered = remember(transactions, range.first, range.second) {
        transactions.filter { t ->
            !t.date.isBefore(range.first) && t.date.isBefore(range.second)
        }
    }
    val incomeTotal = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expenseTotal = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val weekStart = range.first.toLocalDate()
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekLabels = weekDays.map { it.format(DateTimeFormatter.ofPattern("M/d")) }
    val weekExpense = weekDays.map { d ->
        filtered.filter { it.date.toLocalDate() == d && it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
    }
    val weekIncome = weekDays.map { d ->
        filtered.filter { it.date.toLocalDate() == d && it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodNavigator(
            label = formatWeekLabel(range.first.toLocalDate()),
            onPrev = onPrevWeek,
            onNext = onNextWeek,
            allowNext = weekOffset < 0,
            onPickDate = onPickDate
        )
        TrendLineChart("本周每日趋势", weekLabels, weekExpense, weekIncome)
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsContent(
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            filteredTransactions = filtered,
            categoryNames = categoryNames
        )
    }
}

private fun getWeekRange(base: LocalDate, weekOffset: Int): Pair<LocalDateTime, LocalDateTime> {
    val monday = base.plusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
    return Pair(monday.atStartOfDay(), monday.plusWeeks(1).atStartOfDay())
}

private fun formatWeekLabel(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val year = monday.year
    val weekNum = monday.get(WeekFields.of(Locale.getDefault()).weekOfYear())
    return "${year}年 第${weekNum}周  ${monday.monthValue}月${monday.dayOfMonth}日 - ${sunday.monthValue}月${sunday.dayOfMonth}日"
}

// ─── Month ─────────────────────────────────────────────────

@Composable
private fun MonthStatistics(
    transactions: List<Transaction>,
    categoryNames: Map<Long, String>,
    baseDate: LocalDate,
    monthOffset: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPickDate: () -> Unit
) {
    val range = getMonthRange(baseDate, monthOffset)
    val filtered = remember(transactions, range.first, range.second) {
        transactions.filter { t ->
            !t.date.isBefore(range.first) && t.date.isBefore(range.second)
        }
    }
    val incomeTotal = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expenseTotal = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val monthStart = range.first.toLocalDate()
    val daysInMonth = monthStart.lengthOfMonth()
    val monthDays = (1..daysInMonth).map { monthStart.withDayOfMonth(it) }
    val monthLabels = monthDays.map { it.dayOfMonth.toString() }
    val monthExpense = monthDays.map { d ->
        filtered.filter { it.date.toLocalDate() == d && it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
    }
    val monthIncome = monthDays.map { d ->
        filtered.filter { it.date.toLocalDate() == d && it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodNavigator(
            label = range.first.toLocalDate().run { "${year}年 ${monthValue}月" },
            onPrev = onPrevMonth,
            onNext = onNextMonth,
            allowNext = monthOffset < 0,
            onPickDate = onPickDate
        )
        TrendLineChart("本月每日趋势（日）", monthLabels, monthExpense, monthIncome)
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsContent(
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            filteredTransactions = filtered,
            categoryNames = categoryNames
        )
    }
}

private fun getMonthRange(base: LocalDate, monthOffset: Int): Pair<LocalDateTime, LocalDateTime> {
    val firstDay = base.plusMonths(monthOffset.toLong()).withDayOfMonth(1)
    return Pair(firstDay.atStartOfDay(), firstDay.plusMonths(1).atStartOfDay())
}

// ─── Year ──────────────────────────────────────────────────

@Composable
private fun YearStatistics(
    transactions: List<Transaction>,
    categoryNames: Map<Long, String>,
    baseDate: LocalDate,
    yearOffset: Int,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onPickDate: () -> Unit
) {
    val range = getYearRange(baseDate, yearOffset)
    val filtered = remember(transactions, range.first, range.second) {
        transactions.filter { t ->
            !t.date.isBefore(range.first) && t.date.isBefore(range.second)
        }
    }
    val incomeTotal = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expenseTotal = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    val year = range.first.year
    val yearTxs = transactions.filter { it.date.year == year }
    val monthLabels = (1..12).map { "${it}月" }
    val monthExpense = (1..12).map { m ->
        yearTxs.filter { it.date.monthValue == m && it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
    }
    val monthIncome = (1..12).map { m ->
        yearTxs.filter { it.date.monthValue == m && it.type == TransactionType.INCOME }.sumOf { it.amount }.toFloat()
    }
    val years = ((year - 4)..year).toList()
    val fiveYearLabels = years.map { "$it" }
    val fiveYearExpense = years.map { y ->
        transactions.filter { it.date.year == y && it.type == TransactionType.EXPENSE }.sumOf { it.amount }.toFloat()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodNavigator(
            label = "${range.first.year}年",
            onPrev = onPrevYear,
            onNext = onNextYear,
            allowNext = yearOffset < 0,
            onPickDate = onPickDate
        )
        TrendLineChart("本年各月趋势（月）", monthLabels, monthExpense, monthIncome)
        Spacer(modifier = Modifier.height(8.dp))
        TrendLineChart("近五年年度趋势（年）", fiveYearLabels, fiveYearExpense)
        Spacer(modifier = Modifier.height(8.dp))
        StatisticsContent(
            incomeTotal = incomeTotal,
            expenseTotal = expenseTotal,
            filteredTransactions = filtered,
            categoryNames = categoryNames
        )
    }
}

private fun getYearRange(base: LocalDate, yearOffset: Int): Pair<LocalDateTime, LocalDateTime> {
    val firstDay = base.plusYears(yearOffset.toLong()).withDayOfYear(1)
    return Pair(firstDay.atStartOfDay(), firstDay.plusYears(1).atStartOfDay())
}

// ─── Shared Components ─────────────────────────────────────

@Composable
private fun PeriodNavigator(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    allowNext: Boolean,
    onPickDate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一个")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPickDate) {
                Icon(Icons.Filled.DateRange, contentDescription = "选择日期", tint = MaterialTheme.colorScheme.primary)
            }
            if (allowNext) {
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一个")
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun StatisticsContent(
    incomeTotal: Double,
    expenseTotal: Double,
    filteredTransactions: List<Transaction>,
    categoryNames: Map<Long, String>
) {
    val balance = incomeTotal - expenseTotal
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // 收支概览卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("收支概览", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AmountBlock("收入", incomeTotal, Color(0xFF4CAF50))
                    AmountBlock("支出", expenseTotal, Color(0xFFF44336))
                    AmountBlock("结余", balance, if (balance >= 0) Color(0xFF2196F3) else Color(0xFFF44336))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 分类统计
        if (filteredTransactions.isNotEmpty()) {
            Text(
                "分类统计",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 按类别汇总支出
            val expenseByCategory = filteredTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries
                .sortedByDescending { it.value }

            if (expenseByCategory.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("支出明细", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        expenseByCategory.forEach { (catId, total) ->
                            val name = categoryNames[catId] ?: "未知"
                            val pct = if (expenseTotal > 0) total / expenseTotal * 100 else 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name)
                                Text(
                                    "¥ %.2f  (%.1f%%)".format(total, pct),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 按类别汇总收入
            val incomeByCategory = filteredTransactions
                .filter { it.type == TransactionType.INCOME }
                .groupBy { it.categoryId }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries
                .sortedByDescending { it.value }

            if (incomeByCategory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("收入明细", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        incomeByCategory.forEach { (catId, total) ->
                            val name = categoryNames[catId] ?: "未知"
                            val pct = if (incomeTotal > 0) total / incomeTotal * 100 else 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name)
                                Text(
                                    "¥ %.2f  (%.1f%%)".format(total, pct),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 每日趋势
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "每日趋势",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val dailyExpense = filteredTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.date.toLocalDate() }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries
                .sortedBy { it.key }

            val dailyIncome = filteredTransactions
                .filter { it.type == TransactionType.INCOME }
                .groupBy { it.date.toLocalDate() }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
                .entries
                .sortedBy { it.key }

            if (dailyExpense.isNotEmpty() || dailyIncome.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val allDates = (dailyExpense.map { it.key } + dailyIncome.map { it.key })
                            .distinct()
                            .sorted()
                            .takeLast(14) // show last 14 days at most

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("日期", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text("收入", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), modifier = Modifier.weight(1f))
                            Text("支出", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF44336), modifier = Modifier.weight(1f))
                            Text("净额", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                        }
                        allDates.forEach { date ->
                            val exp = dailyExpense.firstOrNull { it.key == date }?.value ?: 0.0
                            val inc = dailyIncome.firstOrNull { it.key == date }?.value ?: 0.0
                            val rowColor = if (inc - exp >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${date.monthValue}/${date.dayOfMonth}",
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "+¥ %.2f".format(inc),
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "-¥ %.2f".format(exp),
                                    color = Color(0xFFF44336),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "%.2f".format(inc - exp),
                                    color = rowColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.7f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "暂无数据",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AmountBlock(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "¥ %.2f".format(amount),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}