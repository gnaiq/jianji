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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
    onAddSubCategory: (String, String, String, TransactionType, Long) -> Unit = { _, _, _, _, _ -> },
    onDeleteCategory: (Category) -> Unit = {},
    onUpdateCategory: (Category) -> Unit = {},
    onMoveCategory: (Category, Int) -> Unit = { _, _ -> },
    showAddCategoryDialog: Boolean = false,
    onDismissAddDialog: () -> Unit = {},
    onTypeChanged: (TransactionType) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var addingSubFor by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var expandedMajors by remember { mutableStateOf(setOf<Long>()) }

    remember(selectedType) { onTypeChanged(selectedType) }

    val ct = if (selectedType == TransactionType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
    val filtered = categories.filter { it.type == ct }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "分类",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

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

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无分类，点击右下角 + 添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ct == CategoryType.EXPENSE) {
                    val majors = filtered.filter { it.isMajor }.sortedBy { it.sortOrder }
                    items(majors, key = { it.id }) { major ->
                        val subs = filtered.filter { it.parentId == major.id }.sortedBy { it.sortOrder }
                        val expanded = expandedMajors.contains(major.id)
                        MajorCategoryCard(
                            major = major,
                            subs = subs,
                            expanded = expanded,
                            onToggle = {
                                expandedMajors = if (expanded) expandedMajors - major.id else expandedMajors + major.id
                            },
                            onEditMajor = { editingCategory = major },
                            onDeleteMajor = { deletingCategory = major },
                            onMoveMajor = { delta -> onMoveCategory(major, delta) },
                            onAddSub = { addingSubFor = major },
                            onEditSub = { editingCategory = it },
                            onDeleteSub = { deletingCategory = it },
                            onMoveSub = { sub, delta -> onMoveCategory(sub, delta) }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { category ->
                        CategoryItemCard(
                            category = category,
                            onEdit = { editingCategory = category },
                            onDelete = { deletingCategory = category },
                            onMove = { delta -> onMoveCategory(category, delta) }
                        )
                    }
                }
            }
        }
    }

    if (showAddCategoryDialog) {
        RichCategoryFormDialog(
            title = "添加分类",
            categoryType = selectedType,
            onConfirm = { name, icon, _ -> onAddCategory(name, icon, selectedType) },
            onDismiss = onDismissAddDialog
        )
    }

    if (editingCategory != null) {
        val ec = editingCategory!!
        RichCategoryFormDialog(
            title = "编辑分类",
            categoryType = if (ec.type == CategoryType.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME,
            initialName = ec.name,
            initialIcon = ec.icon,
            initialColor = ec.color,
            onConfirm = { name, icon, color ->
                onUpdateCategory(ec.copy(name = name, icon = icon, color = color))
                editingCategory = null
            },
            onDismiss = { editingCategory = null }
        )
    }

    if (addingSubFor != null) {
        val parent = addingSubFor!!
        RichCategoryFormDialog(
            title = "添加小类",
            categoryType = selectedType,
            onConfirm = { name, icon, _ -> onAddSubCategory(name, icon, parent.color, selectedType, parent.id) },
            onDismiss = { addingSubFor = null }
        )
    }

    // 删除确认：分类删除会级联删除其下全部交易记录（外键 CASCADE），必须二次确认
    if (deletingCategory != null) {
        val dc = deletingCategory!!
        val subCount = categories.count { it.parentId == dc.id }
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("删除分类「${dc.icon} ${dc.name}」？") },
            text = {
                Text(
                    buildString {
                        append("该分类下的所有交易记录将被一并永久删除，无法恢复。")
                        if (subCount > 0) {
                            append("\n\n此大类还包含 $subCount 个小类，小类及其交易记录也会同时删除。")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(dc)
                    deletingCategory = null
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MoveButtons(onMove: (Int) -> Unit) {
    Row {
        IconButton(onClick = { onMove(-1) }) {
            Icon(Icons.Default.ArrowUpward, contentDescription = "上移", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { onMove(1) }) {
            Icon(Icons.Default.ArrowDownward, contentDescription = "下移", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CategoryItemCard(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.icon, style = MaterialTheme.typography.titleMedium)
            Text(
                category.name,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            MoveButtons(onMove)
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MajorCategoryCard(
    major: Category,
    subs: List<Category>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditMajor: () -> Unit,
    onDeleteMajor: () -> Unit,
    onMoveMajor: (Int) -> Unit,
    onAddSub: () -> Unit,
    onEditSub: (Category) -> Unit,
    onDeleteSub: (Category) -> Unit,
    onMoveSub: (Category, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp).clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "展开",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(major.icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    major.name,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                MoveButtons(onMoveMajor)
                IconButton(onClick = onEditMajor) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDeleteMajor) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (subs.isEmpty()) {
                        Text(
                            "暂无小类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    } else {
                        subs.forEach { sub ->
                            CategoryItemCard(
                                category = sub,
                                onEdit = { onEditSub(sub) },
                                onDelete = { onDeleteSub(sub) },
                                onMove = { delta -> onMoveSub(sub, delta) }
                            )
                        }
                    }
                    TextButton(onClick = onAddSub) {
                        Icon(Icons.Default.Add, contentDescription = "添加小类", tint = MaterialTheme.colorScheme.primary)
                        Text("添加小类", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// 分类可选图标（150 种），emoji 字符串与原有数据模型完全兼容，无需迁移
private val CATEGORY_ICONS = listOf(
    "🍔","🍕","🍟","🌭","🍿","🥓","🥞","🧇","🥐","🍞",
    "🥪","🥙","🌮","🌯","🥗","🍝","🍜","🍲","🍣","🍱",
    "🍛","🍚","🍙","🍘","🍥","🍡","🍧","🍨","🍦","🥧",
    "🍰","🎂","🍫","🍬","🍭","🍩","🍪","☕","🍵","🧃",
    "🥤","🍺","🍻","🍷","🥂","🍸","🍹","🏠","🏡","🏢",
    "🏬","🏦","🏥","🏨","🏪","🏫","🏭","🏰","💒","⛪",
    "🕌","🛕","🗼","🗽","🌋","🏖️","🏝️","🏔️","🌄","🌅",
    "🌆","🌇","🌃","🌉","🚌","🚏","🚗","🚕","🚙","🚚",
    "🚛","🚜","🛵","🏍️","🚲","🛴","✈️","🚀","🚁","🚂",
    "🚆","🚇","⚓","⛵","🚤","🛳️","🚥","🅿️","⛽","🔧",
    "🔨","🛠️","⚙️","💡","🔌","🔋","📱","💻","⌨️","🖥️",
    "🖨️","📷","📹","🎥","📺","📻","🎵","🎶","🎤","🎧",
    "🎮","🕹️","🎲","♟️","🎯","⚽","🏀","🏈","⚾","🎾",
    "🏐","🏉","🎱","🏓","🏸","🥅","🏒","🏑","🥍","🏹",
    "🎣","🎿","⛷️","🏂","🛷","🥊","🥋","🎽","⛸️","🛹"
)

@Composable
fun RichCategoryFormDialog(
    title: String,
    categoryType: TransactionType,
    initialName: String = "",
    initialIcon: String = "📁",
    initialColor: String = "#6200EE",
    onConfirm: (name: String, icon: String, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    val icons = CATEGORY_ICONS
    val colors = listOf("#E57373","#F06292","#BA68C8","#9575CD","#7986CB","#64B5F6","#4FC3F7","#4DB6AC","#81C784","#AED581","#FFD54F","#FFB74D","#FF8A65","#A1887F","#90A4AE","#E0E0E0")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("分类名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("选择图标", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.height(220.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(icons) { icon ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable { selectedIcon = icon },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedIcon == icon)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(icon, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                Text("选择颜色", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { color ->
                        Card(
                            modifier = Modifier.size(36.dp).clickable { selectedColor = color },
                            colors = CardDefaults.cardColors(containerColor = Color(android.graphics.Color.parseColor(color))),
                            shape = RoundedCornerShape(8.dp)
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedIcon, selectedColor) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}