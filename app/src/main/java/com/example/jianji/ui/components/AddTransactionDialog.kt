package com.example.jianji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.jianji.data.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    categories: List<Category>,
    editingTransaction: Transaction? = null,
    templates: List<QuickTemplate> = emptyList(),
    accounts: List<Account> = emptyList(),
    accountBalances: Map<Long, Double> = emptyMap(),
    tags: List<Tag> = emptyList(),
    initialTagIds: List<Long> = emptyList(),
    onRequestAddTag: () -> Unit = {},
    onDismiss: () -> Unit,
    onRequestAddCategory: (TransactionType) -> Unit = {},
    topCategoryIds: Map<TransactionType, List<Long>> = emptyMap(),
    onConfirm: (categoryId: Long, amount: Double, type: TransactionType, description: String, date: LocalDateTime, accountId: Long?, toAccountId: Long?, tagIds: List<Long>) -> Unit
) {
    var selectedType by remember { mutableStateOf(editingTransaction?.type ?: TransactionType.EXPENSE) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(editingTransaction?.categoryId) }
    var selectedAccountId by remember { mutableStateOf<Long?>(editingTransaction?.accountId) }
    var selectedToAccountId by remember { mutableStateOf<Long?>(editingTransaction?.toAccountId) }
    var amount by remember { mutableStateOf(editingTransaction?.amountCents?.let { (it / 100.0).toString() } ?: "") }
    var description by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedDate by remember { mutableStateOf(editingTransaction?.date ?: LocalDateTime.now()) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    // §6 标签多选
    var selectedTagIds by remember { mutableStateOf(initialTagIds.toSet()) }

    // 自动选择默认分类
    LaunchedEffect(categories, selectedType) {
        if (categories.isEmpty() || selectedType == TransactionType.TRANSFER) return@LaunchedEffect
        val ct = if (selectedType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        val current = categories.find { it.id == selectedCategoryId }
        if (current == null || current.type != ct) {
            selectedCategoryId = (categories.firstOrNull { it.type == ct && !it.isMajor }
                ?: categories.firstOrNull { it.type == ct })?.id
        }
    }

    val ct = if (selectedType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
    val filteredCategories = categories.filter { it.type == ct }
    val filteredTemplates = templates.filter { it.type == selectedType }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val transferCategory = categories.firstOrNull { it.isSystem }
    // 高频分类：按交易笔数降序（有历史数据）；若无则回退到 sortOrder 前 6
    val topCategories = remember(filteredCategories, topCategoryIds, selectedType) {
        val freqIds = topCategoryIds[selectedType] ?: emptyList()
        if (freqIds.isNotEmpty()) {
            val catMap = categories.associateBy { it.id }
            freqIds.mapNotNull { catMap[it] }.filter { !it.isMajor }.take(6)
        } else {
            filteredCategories.filter { !it.isMajor }.take(6)
        }
    }
    // 支持计算器表达式（如 12+3.5）；纯数字也能直接解析
    val parsedAmount = evalExpression(amount) ?: amount.toDoubleOrNull() ?: 0.0
    // 金额格式错误提示：表达式解析失败且无法作为纯数字解析时显示错误
    val isAmountError = amount.isNotEmpty() && evalExpression(amount) == null && amount.toDoubleOrNull() == null
    val isValid = if (selectedType == TransactionType.TRANSFER) {
        parsedAmount > 0 && selectedAccountId != null && selectedToAccountId != null
            && selectedAccountId != selectedToAccountId && transferCategory != null
    } else {
        selectedCategory != null && parsedAmount > 0 && parsedAmount <= 99_999_999.99
    }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // 动态计算器键盘：点击 🧮 时弹出（v2 §5.2 渐进披露，不再常驻遮挡描述框）
    var showCalculator by remember { mutableStateOf(false) }
    // 更多选项折叠状态（v2 §5.4 / B-4）：日期/标签/描述默认收起
    var showMore by remember { mutableStateOf(false) }

    // 全屏表单：用 Dialog 取代 ModalBottomSheet，消除底部弹层上方 scrim 区域；
    // onDismissRequest = {} 屏蔽「点外部 / 返回键」误触关闭，仅界面内显式按钮（关闭 X / 取消）可退出
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editingTransaction != null) "编辑交易" else "添加交易",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
            }
                // 收支类型（含转账）：§5 改用分段选择器——天生单行等宽成组，
                // 杜绝窄屏/大字体下 Button 文字换行导致的「视觉塌成竖排」
                val types = listOf(
                    TransactionType.INCOME to "收入",
                    TransactionType.EXPENSE to "支出",
                    TransactionType.TRANSFER to "转账"
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, (type, label) ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                            colors = SegmentedButtonDefaults.colors(
                                // 保留原三色语义：收入=主色 / 支出=错误色 / 转账=第三色
                                activeContainerColor = when (type) {
                                    TransactionType.INCOME -> MaterialTheme.colorScheme.primaryContainer
                                    TransactionType.EXPENSE -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.tertiaryContainer
                                }
                            )
                        ) { Text(label, maxLines = 1, softWrap = false) }
                    }
                }

                // 快捷模板
                if (filteredTemplates.isNotEmpty()) {
                    Text("快捷模板", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredTemplates) { template ->
                            val cat = categories.find { it.id == template.categoryId }
                            Card(
                                modifier = Modifier.clickable {
                                    selectedCategoryId = template.categoryId
                                    amount = (template.amountCents / 100.0).toString()
                                    description = template.description
                                    selectedAccountId = template.accountId
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(cat?.icon ?: "📁")
                                    Column {
                                        Text(
                                            template.description.ifEmpty { cat?.name ?: "" },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            "¥${"%.2f".format(template.amountCents / 100.0)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (template.type == TransactionType.EXPENSE)
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 分类选择（转账无需分类）——v2 §5.3 高频 Chip + 更多
                if (selectedType != TransactionType.TRANSFER) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(topCategories) { cat ->
                            val selected = cat.id == selectedCategoryId
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text("${cat.icon} ${cat.name}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showCategoryPicker = true },
                                label = { Text("更多") },
                                leadingIcon = { Icon(Icons.Default.Add, null) }
                            )
                        }
                    }
                }

                // 账户选择
                if (selectedType == TransactionType.TRANSFER) {
                    if (accounts.size < 2) {
                        Text("转账需要至少两个账户，请先在「设置 → 账户管理」中添加",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        TransferAccountPicker("转出账户", selectedAccountId, accounts, accountBalances) { selectedAccountId = it }
                        TransferAccountPicker("转入账户", selectedToAccountId, accounts, accountBalances) { selectedToAccountId = it }
                    }
                } else if (accounts.isNotEmpty()) {
                    var showAccountPicker by remember { mutableStateOf(false) }
                    val selectedAccount = accounts.find { it.id == selectedAccountId }
                        ?: accounts.firstOrNull { it.isDefault }

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { showAccountPicker = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedAccount?.icon ?: "💳"} ${selectedAccount?.name ?: "无账户"}  ${selectedAccount?.id?.let { "¥%.2f".format(accountBalances[it] ?: 0.0) } ?: ""}",
                                style = MaterialTheme.typography.bodyLarge)
                            Text("选择账户 >", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (showAccountPicker) {
                        AlertDialog(
                            onDismissRequest = { showAccountPicker = false },
                            title = { Text("选择账户") },
                            text = {
                                LazyColumn {
                                    items(accounts) { acc ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    selectedAccountId = acc.id
                                                    showAccountPicker = false
                                                }
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (acc.id == selectedAccountId)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Text(acc.icon, style = MaterialTheme.typography.bodyLarge)
                                                Column {
                                                    Text(acc.name, style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Bold)
                                                    Text("¥%.2f".format(accountBalances[acc.id] ?: 0.0),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showAccountPicker = false }) { Text("取消") }
                            }
                        )
                    }
                }

                // 金额输入：始终可见，0 点击可输入
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.length <= 16 && (it.isEmpty() || it.matches(Regex("^[0-9.]*$")))) amount = it
                    },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isAmountError,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    trailingIcon = {
                        IconButton(onClick = { showCalculator = true }) {
                            Icon(Icons.Filled.Calculate, contentDescription = "打开计算器")
                        }
                    }
                )

                // 金额格式错误提示
                if (isAmountError) {
                    Text(
                        "金额格式无效，请输入数字或计算表达式（如 12+3.5）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // 标签多选（§6）：始终可见
                Text("标签", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        val selected = tag.id in selectedTagIds
                        val tagColor = runCatching { Color(android.graphics.Color.parseColor(tag.color)) }
                            .getOrDefault(MaterialTheme.colorScheme.primary)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedTagIds = if (selected) selectedTagIds - tag.id else selectedTagIds + tag.id
                            },
                            label = { Text("${tag.icon} ${tag.name}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tagColor.copy(alpha = 0.25f),
                                selectedLabelColor = tagColor
                            )
                        )
                    }
                    AssistChip(
                        onClick = onRequestAddTag,
                        label = { Text("+ 新建标签") },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                }

                // 「更多选项」折叠区：日期、描述默认收起
                TextButton(
                    onClick = { showMore = !showMore },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (showMore) "收起更多选项 ▲" else "更多选项（日期 / 描述）▼") }

                if (showMore) {
                // 日期时间选择
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            Calendar.getInstance().apply {
                                set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                            }.let { cal ->
                                android.app.DatePickerDialog(context,
                                    { _, y, m, d ->
                                        selectedDate = selectedDate.withYear(y).withMonth(m + 1).withDayOfMonth(d)
                                    },
                                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("日期", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, selectedDate.hour)
                                set(Calendar.MINUTE, selectedDate.minute)
                            }.let { cal ->
                                android.app.TimePickerDialog(context,
                                    { _, h, m ->
                                        selectedDate = selectedDate.withHour(h).withMinute(m).withSecond(0).withNano(0)
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
                                ).show()
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("时间", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(selectedDate.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // 描述
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 100) description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
                } // end if (showMore)

                // 分类选择器
                if (showCategoryPicker) {
                    CategoryPickerDialog(
                        categories = filteredCategories,
                        onSelect = { selectedCategoryId = it.id; showCategoryPicker = false },
                        onRequestAdd = { onRequestAddCategory(selectedType) },
                        onDismiss = { showCategoryPicker = false }
                    )
                }

                // 底部单一保存键（v2 §5.4 / B-5）：去掉取消键，关闭走顶部 X
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (!isValid) return@Button
                        val categoryId = if (selectedType == TransactionType.TRANSFER)
                            transferCategory?.id ?: 0L else (selectedCategory?.id ?: 0L)
                        onConfirm(
                            categoryId, parsedAmount, selectedType, description,
                            selectedDate.withNano(0), selectedAccountId,
                            if (selectedType == TransactionType.TRANSFER) selectedToAccountId else null,
                            selectedTagIds.toList()
                        )
                    },
                    enabled = isValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (editingTransaction != null) "保存修改" else "记一笔", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(16.dp))
            }
            if (showCalculator) {
                // 同窗口底部浮层：遮罩 + 底部锚定计算器面板，规避嵌套 ModalBottomSheet 的窗口层级/焦点问题
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clickable { showCalculator = false }
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (amount.isEmpty()) "0.00" else amount,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    maxLines = 1
                                )
                                CalculatorKeypad { key ->
                                    val ops = setOf('+', '−', '×', '÷')
                                    when (key) {
                                        "⌫" -> amount = if (amount.isNotEmpty()) amount.dropLast(1) else ""
                                        "C" -> amount = ""
                                        else -> {
                                            val last = amount.lastOrNull()
                                            when {
                                                key in listOf("+", "−", "×", "÷") -> {
                                                    // 实时求值：先计算当前表达式再追加运算符
                                                    if (amount.isNotEmpty() && last !in ops && last != '.') {
                                                        val r = evalExpression(amount)
                                                        if (r != null) amount = formatCalc(r)
                                                        amount += key
                                                    }
                                                }
                                                key == "." -> {
                                                    val seg = amount.split("[-+×÷]".toRegex()).last()
                                                    amount += if (amount.isEmpty() || last in ops) "0." else if ("." !in seg) "." else ""
                                                }
                                                else -> amount += key
                                            }
                                        }
                                    }
                                }
                                Button(onClick = { showCalculator = false }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }
    }

// 计算器底层常量与工具函数

private val EXPR_PATTERN = Regex("^[0-9.+\\-*/ ]+$")

// 计算器键盘布局（不含 =，实时求值替代）
private val CALC_KEYPAD_ROWS = listOf(
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "−"),
    listOf("C", "0", ".", "+"),
    listOf("⌫")
)

// 计算器表达式求值（支持 + − × ÷，含运算符优先级），非法返回 null
private fun evalExpression(input: String): Double? {
    val s = input.replace('×', '*').replace('÷', '/')
    if (s.isBlank() || !s.matches(EXPR_PATTERN)) return null
    return try {
        val tokens = mutableListOf<String>()
        var num = ""
        for (ch in s) {
            if (ch.isDigit() || ch == '.') num += ch
            else {
                if (num.isNotEmpty()) { tokens.add(num); num = "" }
                if (ch != ' ') tokens.add(ch.toString())
            }
        }
        if (num.isNotEmpty()) tokens.add(num)
        if (tokens.isEmpty()) return null
        val nums = mutableListOf(tokens[0].toDouble())
        val ops = mutableListOf<String>()
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val nxt = tokens[i + 1].toDouble()
            if (op == "*" || op == "/") {
                val a = nums.removeAt(nums.lastIndex)
                if (op == "/" && nxt == 0.0) return null
                nums.add(if (op == "*") a * nxt else a / nxt)
            } else {
                nums.add(nxt); ops.add(op)
            }
            i += 2
        }
        var res = nums[0]
        for (k in ops.indices) res = if (ops[k] == "+") res + nums[k + 1] else res - nums[k + 1]
        res
    } catch (e: Exception) { null }
}

private fun formatCalc(v: Double): String {
    val value = if (v.isNaN() || v.isInfinite()) 0.0 else v
    val bd = java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP)
    return if (bd.stripTrailingZeros().scale() <= 0) bd.toBigInteger().toString()
    else String.format(java.util.Locale.US, "%.2f", value)
}

@Composable
private fun CalculatorKeypad(onInput: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CALC_KEYPAD_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onInput(key) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (key in listOf("+", "−", "×", "÷"))
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferAccountPicker(
    label: String,
    selectedId: Long?,
    accounts: List<Account>,
    accountBalances: Map<Long, Double>,
    onSelect: (Long) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    val sel = accounts.find { it.id == selectedId }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { show = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${sel?.icon ?: "💳"} ${sel?.name ?: label}  ${sel?.id?.let { "¥%.2f".format(accountBalances[it] ?: 0.0) } ?: ""}",
                style = MaterialTheme.typography.bodyLarge)
            Text("选择 >", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(label) },
            text = {
                LazyColumn {
                    items(accounts) { acc ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(acc.id); show = false }
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (acc.id == selectedId)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(acc.icon, style = MaterialTheme.typography.bodyLarge)
                                Column {
                                    Text(acc.name, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold)
                                    Text("¥%.2f".format(accountBalances[acc.id] ?: 0.0),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { show = false }) { Text("取消") } }
        )
    }
}

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onSelect: (Category) -> Unit,
    onRequestAdd: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择分类") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val majors = categories.filter { it.isMajor }.sortedBy { it.sortOrder }
                val majorIds = majors.map { it.id }
                val orphans = categories.filter { !it.isMajor && it.parentId !in majorIds }
                items(majors, key = { it.id }) { major ->
                    val subs = categories.filter { it.parentId == major.id }.sortedBy { it.sortOrder }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            major.name,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        (if (subs.isEmpty()) listOf(major) else subs).forEach { cat ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(cat) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Text(cat.icon, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                items(orphans, key = { it.id }) { cat ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(cat) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(cat.icon, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onRequestAdd() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加分类", tint = MaterialTheme.colorScheme.primary)
                            Text("添加新分类", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun CategoryFormDialog(
    title: String,
    categoryType: TransactionType,
    onConfirm: (name: String, icon: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("📁") }
    val icons = listOf("🍔","🍕","🚌","🏥","🎮","📚","👕","💄","🏠","⚡","📱","🎵","✈️","🎁","💊","🏋️","🐱","☕","💻","🚗")
    val iconMap = icons.associateBy { it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("分类名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("选择图标", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons) { icon ->
                        Card(
                            modifier = Modifier.clickable { selectedIcon = icon },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedIcon == icon)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(icon, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon) },
                enabled = name.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}