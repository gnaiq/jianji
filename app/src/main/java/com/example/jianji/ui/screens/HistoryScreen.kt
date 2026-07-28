package com.example.jianji.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 历史交易：列出全部交易（按日期分组、可搜索），点击进入查看/修改，
 * 滑动删除。复用 HomeScreen 的 SwipeToDeleteItem 与现有编辑弹窗，保持行为一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    accounts: List<Account> = emptyList(),
    onTransactionClick: (Transaction) -> Unit = {},
    onDeleteTransaction: (Transaction) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    val filtered = remember(transactions, query) {
        val base = transactions.sortedByDescending { it.date }
        if (query.isBlank()) base else base.filter { tx ->
            val cat = categoryMap[tx.categoryId]
            cat?.name?.contains(query, ignoreCase = true) == true ||
                tx.description.contains(query, ignoreCase = true)
        }
    }

    // 按日期分组：相邻同日期只显示一次日期头
    val rows = remember(filtered) {
        val list = mutableListOf<Any>()
        var lastDate: LocalDate? = null
        for (tx in filtered) {
            val d = tx.date.toLocalDate()
            if (d != lastDate) {
                list.add(d)
                lastDate = d
            }
            list.add(tx)
        }
        list
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("搜索描述或分类...") },
            leadingIcon = { Icon(Icons.Default.Search, "搜索") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "还没有任何交易记录" else "无匹配交易",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(rows, key = {
                    when (it) {
                        is LocalDate -> "h_$it"
                        is Transaction -> it.id
                        else -> it.hashCode()
                    }
                }) { row ->
                    when (row) {
                        is LocalDate -> {
                            Text(
                                row.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE")),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        is Transaction -> {
                            SwipeToDeleteItem(
                                transaction = row,
                                category = categoryMap[row.categoryId],
                                accountName = row.accountId?.let { accountMap[it]?.name },
                                onClick = { onTransactionClick(row) },
                                onDelete = { onDeleteTransaction(row) }
                            )
                        }
                    }
                }
            }
        }
    }
}
