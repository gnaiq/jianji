package com.example.jianji.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.Category
import com.example.jianji.data.CategoryType
import com.example.jianji.data.TransactionType

@Composable
fun CategoryManagementScreen(
    categories: List<Category> = emptyList(),
    onAddCategory: (String, String, TransactionType) -> Unit = { _, _, _ -> },
    onDeleteCategory: (Category) -> Unit = {},
    onUpdateCategory: (Category) -> Unit = {},
    showAddCategoryDialog: Boolean = false,
    onDismissAddDialog: () -> Unit = {},
    onTypeChanged: (TransactionType) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    remember(selectedType) { onTypeChanged(selectedType) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("分类管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedType = TransactionType.INCOME },
                modifier = Modifier.weight(1f),
                enabled = selectedType != TransactionType.INCOME,
                shape = RoundedCornerShape(8.dp)
            ) { Text("收入分类") }
            Button(
                onClick = { selectedType = TransactionType.EXPENSE },
                modifier = Modifier.weight(1f),
                enabled = selectedType != TransactionType.EXPENSE,
                shape = RoundedCornerShape(8.dp)
            ) { Text("支出分类") }
        }

        val ct = if (selectedType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
        val filtered = categories.filter { it.type == ct }
        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("暂无分类，点击右下角 + 添加", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { category ->
                    CategoryItemCard(
                        category = category,
                        onEdit = { editingCategory = category },
                        onDelete = { onDeleteCategory(category) }
                    )
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        RichCategoryFormDialog(
            title = "添加分类",
            categoryType = selectedType,
            onConfirm = { name, icon ->
                onAddCategory(name, icon, selectedType)
                onDismissAddDialog()
            },
            onDismiss = onDismissAddDialog
        )
    }

    val editCat = editingCategory
    if (editCat != null) {
        RichCategoryFormDialog(
            title = "编辑分类",
            categoryType = selectedType,
            initialName = editCat.name,
            initialIcon = editCat.icon,
            onConfirm = { name, icon ->
                onUpdateCategory(editCat.copy(name = name, icon = icon))
                editingCategory = null
            },
            onDismiss = { editingCategory = null }
        )
    }
}

@Composable
fun CategoryItemCard(
    category: Category,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = category.icon, modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
                Column {
                    Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (category.isDefault) "默认分类" else "自定义",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                }
                if (!category.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = Color.Red)
                    }
                }
            }
        }
    }
}

/** 图标分组 */
data class IconGroup(val label: String, val icons: List<String>)

val iconLibrary: List<IconGroup> = listOf(
    IconGroup("生活", listOf("🏠","👕","🛏️","🪥","🧴","🪞","🧹","🧺","🚿","🛒","🎂","☕","🍵","🍺","🍚","🥡")),
    IconGroup("交通", listOf("🚗","🚙","🏍️","🚲","⛽","🔧","🅿️","🚌","🚕","✈️","🚄","🚢")),
    IconGroup("学习", listOf("📚","📖","✏️","🎓","🏫","📝","🖊️","📐","🔬","🧪","🎒")),
    IconGroup("科技", listOf("📱","💻","🖥️","⌨️","🖱️","📷","🎧","🔌","🔋","📡","⌚","🎮","🤖")),
    IconGroup("医疗", listOf("🏥","💊","💉","🩺","🩻","💗","🧬","🦷","👁️")),
    IconGroup("金融", listOf("💰","💳","🏦","📊","💵","💎","🪙","🧧","📈")),
    IconGroup("娱乐", listOf("🎮","🎬","🎵","🎤","🎸","🎹","🎯","🎲","🏀","⚽","🎾")),
    IconGroup("购物", listOf("🛍️","🛒","👗","👠","💄","💍","👜","🎁","👟","🧥")),
    IconGroup("餐饮", listOf("🍔","🍕","🍣","🍜","🥗","🍰","☕","🍺","🧋","🥤","🍱","🥩")),
)

val allIconsFlat: List<String> = iconLibrary.flatMap { it.icons }.distinct()

/** 丰富的分类表单对话框（带图标分组选择） */
@Composable
fun RichCategoryFormDialog(
    title: String,
    categoryType: TransactionType,
    initialName: String = "",
    initialIcon: String = "💰",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryName by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    val scrollState = rememberScrollState()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = categoryName, onValueChange = { categoryName = it },
                    label = { Text("分类名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("选择图标", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                iconLibrary.forEach { group ->
                    Text(group.label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    val rows = group.icons.chunked(7)
                    rows.forEach { rowIcons ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowIcons.forEach { icon ->
                                Card(
                                    modifier = Modifier.size(40.dp).clickable { selectedIcon = icon },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedIcon == icon)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(icon, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            repeat(7 - rowIcons.size) { Box(modifier = Modifier.size(40.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (categoryName.isNotEmpty()) onConfirm(categoryName.trim(), selectedIcon) }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
