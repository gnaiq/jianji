package com.example.jianji.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.local.entity.Category
import com.example.jianji.data.local.entity.Transaction
import com.example.jianji.data.TransactionType
import com.example.jianji.ui.viewmodel.TransactionViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    transactionVM: TransactionViewModel,
    categories: List<Category> = emptyList(),
    onBack: () -> Unit = {}
) {
    val deleted by transactionVM.deletedTransactions.collectAsState()
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                Text("回收站", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (deleted.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) { Text("清空") }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (deleted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("回收站为空", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(deleted) { tx ->
                    RecycleItem(
                        tx = tx,
                        categoryName = categoryMap[tx.categoryId]?.name ?: "未分类",
                        categoryIcon = categoryMap[tx.categoryId]?.icon ?: "💰",
                        onRestore = { transactionVM.restoreTransaction(tx.id) },
                        onDelete = { transactionVM.deleteTransaction(tx) }
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空回收站") },
            text = { Text("将彻底删除 ${deleted.size} 笔交易，此操作不可恢复。确定吗？") },
            confirmButton = {
                Button(onClick = { showClearConfirm = false; transactionVM.purgeDeleted() }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun RecycleItem(
    tx: Transaction,
    categoryName: String,
    categoryIcon: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = tx.type == TransactionType.EXPENSE
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(categoryIcon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    tx.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                (if (isExpense) "-¥" else "+¥") + String.format("%.2f", tx.amountCents / 100.0),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (isExpense) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRestore) { Icon(Icons.Default.RestoreFromTrash, "还原") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteForever, "彻底删除") }
        }
    }
}
