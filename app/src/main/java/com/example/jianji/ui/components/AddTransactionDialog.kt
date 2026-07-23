package com.example.jianji.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.Category
import com.example.jianji.data.CategoryType
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun AddTransactionDialog(
    categories: List<Category>,
    editingTransaction: Transaction? = null,
    onDismiss: () -> Unit,
    onRequestAddCategory: (CategoryType) -> Unit = {},
    onConfirm: (categoryId: Long, amount: Double, type: TransactionType, description: String, date: LocalDateTime) -> Unit
) {
    var selectedType by remember { mutableStateOf(editingTransaction?.type ?: TransactionType.EXPENSE) }
    // 用 id 跟踪选中分类，避免 categories 流刷新后对象身份变化导致丢失
    var selectedCategoryId by remember { mutableStateOf<Long?>(editingTransaction?.categoryId) }
    var amount by remember { mutableStateOf(editingTransaction?.amount?.toString() ?: "") }
    var description by remember { mutableStateOf(editingTransaction?.description ?: "") }
    var selectedDate by remember { mutableStateOf(editingTransaction?.date ?: LocalDateTime.now()) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    // 自动选择默认分类：打开时/切换收支类型时，若无有效选择则选中该类型第一个分类
    LaunchedEffect(categories, selectedType) {
        if (categories.isEmpty()) return@LaunchedEffect
        val current = categories.find { it.id == selectedCategoryId }
        if (current == null || current.type != selectedType) {
            selectedCategoryId = categories.firstOrNull { it.type == selectedType }?.id
        }
    }

    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val filteredCategories = categories.filter { it.type == selectedType }
    val parsedAmount = amount.toDoubleOrNull() ?: 0.0
    val isValid = selectedCategory != null && parsedAmount > 0

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingTransaction != null) "编辑交易" else "添加交易") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        enabled = selectedType != TransactionType.INCOME
                    ) {
                        Text("收入")
                    }
                    Button(
                        onClick = { selectedType = TransactionType.EXPENSE },
                        modifier = Modifier.weight(1f),
                        enabled = selectedType != TransactionType.EXPENSE
                    ) {
                        Text("支出")
                    }
                }

                // 分类选择
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCategoryPicker = true },
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
                            text = selectedCategory?.name ?: "选择分类",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = selectedCategory?.icon ?: "📁",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // 日期选择
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply {
                                set(
                                    selectedDate.year,
                                    selectedDate.monthValue - 1,
                                    selectedDate.dayOfMonth
                                )
                            }
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedDate = selectedDate
                                        .withYear(y)
                                        .withMonth(m + 1)
                                        .withDayOfMonth(d)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
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
                            text = "日期",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            style = MaterialTheme.typography.bodyLarge
                        )
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                        onSelect = {
                            selectedCategoryId = it.id
                            showCategoryPicker = false
                        },
                        onRequestAdd = { onRequestAddCategory(selectedType) },
                        onDismiss = { showCategoryPicker = false }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val category = selectedCategory
                    if (category != null && isValid) {
                        onConfirm(
                            category.id,
                            parsedAmount,
                            selectedType,
                            description,
                            selectedDate.withNano(0)
                        )
                    }
                },
                enabled = isValid
            ) {
                Text(if (editingTransaction != null) "保存" else "确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(category) },
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
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = category.icon,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                // 新增分类入口
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestAdd() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加分类",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "添加新分类",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
