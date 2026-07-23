package com.example.jianji.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun AddTransactionDialog(
    categories: List<Category>,
    editingTransaction: Transaction? = null,
    templates: List<QuickTemplate> = emptyList(),
    accounts: List<Account> = emptyList(),
    onDismiss: () -> Unit,
    onRequestAddCategory: (TransactionType) -> Unit = {},
    onConfirm: (categoryId: Long, amount: Double, type: TransactionType, description: String, date: LocalDateTime, accountId: Long?) -> Unit
) {
    var selectedType by remember { mutableStateOf(editingTransaction?.type ?: TransactionType.EXPENSE) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(editingTransaction?.categoryId) }
    var selectedAccountId by remember { mutableStateOf<Long?>(editingTransaction?.accountId) }
    var amount by remember { mutableStateOf(editingTransaction?.amount?.toString() ?: "") }
    var description by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedDate by remember { mutableStateOf(editingTransaction?.date ?: LocalDateTime.now()) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // 自动选择默认分类
    LaunchedEffect(categories, selectedType) {
        if (categories.isEmpty()) return@LaunchedEffect
        val current = categories.find { it.id == selectedCategoryId }
        if (current == null || current.type != selectedType) {
            selectedCategoryId = categories.firstOrNull { it.type == selectedType }?.id
        }
    }

    val filteredCategories = categories.filter { it.type == selectedType }
    val filteredTemplates = templates.filter { it.type == selectedType }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    val isValid = selectedCategory != null && parsedAmount > 0

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingTransaction != null) "编辑交易" else "添加交易") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 收支类型
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedType = TransactionType.INCOME },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == TransactionType.INCOME)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text("收入") }
                    Button(
                        onClick = { selectedType = TransactionType.EXPENSE },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedType == TransactionType.EXPENSE)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text("支出") }
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
                                    amount = template.amount.toString()
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
                                            "¥${template.amount.toInt()}",
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

                // 分类选择
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showCategoryPicker = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedCategory?.name ?: "选择分类", style = MaterialTheme.typography.bodyLarge)
                        Text(selectedCategory?.icon ?: "📁", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // 账户选择
                if (accounts.size > 1) {
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
                            Text("${selectedAccount?.icon ?: "💳"} ${selectedAccount?.name ?: "无账户"}",
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
                                                Text(acc.name, style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold)
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

                // 金额输入
                OutlinedTextField(
                    value = amount,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amount = newValue
                        }
                    },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 快捷金额
                LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0)) { quick ->
                        Card(
                            modifier = Modifier.clickable { amount = quick.toString() },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = quick.toInt().toString(),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 描述
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 100) description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 分类选择器
                if (showCategoryPicker) {
                    CategoryPickerDialog(
                        categories = filteredCategories,
                        onSelect = { selectedCategoryId = it.id; showCategoryPicker = false },
                        onRequestAdd = { onRequestAddCategory(selectedType) },
                        onDismiss = { showCategoryPicker = false }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cat = selectedCategory
                    if (cat != null && isValid) {
                        onConfirm(cat.id, parsedAmount, selectedType, description, selectedDate.withNano(0), selectedAccountId)
                    }
                },
                enabled = isValid
            ) { Text(if (editingTransaction != null) "保存" else "确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
                items(categories) { category ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(category) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(category.icon, style = MaterialTheme.typography.bodyLarge)
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
