package com.example.jianji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.Category
import com.example.jianji.data.Tag
import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import com.example.jianji.ui.theme.AppColors
import java.time.format.DateTimeFormatter

/**
 * 交易卡片组件：展示单笔交易的详细信息（分类图标、描述、账户、标签、金额、时间）。
 * 从 HomeScreen 提取，供 HistoryScreen 等复用。
 */
@Composable
fun TransactionItemCard(
    transaction: Transaction,
    category: Category? = null,
    accountName: String? = null,
    toAccountName: String? = null,
    tags: List<Tag> = emptyList(),
    onClick: () -> Unit = {},
    onRequestDelete: () -> Unit = {}
) {
    val isTransfer = transaction.type == TransactionType.TRANSFER
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(
                            if (isTransfer) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isTransfer) "🔄" else (category?.icon ?: "📁"),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (isTransfer) "转账" else (category?.name ?: "未分类"),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val accountLabel = when {
                            isTransfer && accountName != null && toAccountName != null -> "$accountName → $toAccountName"
                            accountName != null -> accountName
                            else -> null
                        }
                        if (accountLabel != null) {
                            Text(accountLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                    if (transaction.description.isNotEmpty()) {
                        Text(transaction.description, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                val tagColor = runCatching {
                                    Color(android.graphics.Color.parseColor(tag.color))
                                }.getOrDefault(MaterialTheme.colorScheme.primary)
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("${tag.icon} ${tag.name}",
                                        style = MaterialTheme.typography.labelSmall) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = tagColor.copy(alpha = 0.15f),
                                        labelColor = tagColor
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        transaction.type == TransactionType.INCOME -> "+¥${formatAmount(transaction.amountCents / 100.0)}"
                        isTransfer -> "⇄ ¥${formatAmount(transaction.amountCents / 100.0)}"
                        else -> "-¥${formatAmount(transaction.amountCents / 100.0)}"
                    },
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    color = when {
                        transaction.type == TransactionType.INCOME -> AppColors.IncomeGreen
                        isTransfer -> MaterialTheme.colorScheme.tertiary
                        else -> AppColors.ExpenseRed
                    }
                )
                Text(transaction.date.format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            IconButton(onClick = onRequestDelete) {
                Icon(Icons.Default.Delete, "删除", tint = AppColors.DeleteRed.copy(alpha = 0.6f))
            }
        }
    }
}