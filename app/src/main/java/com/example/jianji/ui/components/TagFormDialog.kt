package com.example.jianji.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.jianji.data.local.entity.Tag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFormDialog(
    onConfirm: (name: String, color: String, icon: String) -> Unit,
    onDismiss: () -> Unit,
    initial: Tag? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initial?.icon ?: "🏷️") }
    // 图标库：40 个常用 emoji 供选择（满足"至少 20 个"要求），点击即选中
    val tagIcons = listOf(
        "🏷️", "⭐", "💰", "🍔", "🍕", "🚌", "🏥", "🎮", "📚", "👕",
        "💄", "🏠", "⚡", "📱", "🎵", "✈️", "🎁", "💊", "🏋️", "🐱",
        "☕", "💻", "🚗", "🌟", "🔥", "💡", "📌", "🎯", "❤️", "🛒",
        "🍺", "🎬", "✏️", "🔑", "📷", "🎨", "🍎", "🐶", "🌈", "☀️"
    )
    val palette = listOf(
        "#E57373", "#F06292", "#BA68C8", "#9575CD", "#7986CB",
        "#64B5F6", "#4FC3F7", "#4DB6AC", "#81C784", "#FFD54F",
        "#FF8A65", "#A1887F"
    )
    var selectedColor by remember { mutableStateOf(initial?.color ?: palette[0]) }
    val isValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建标签" else "编辑标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("图标", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.height(200.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 42.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(tagIcons) { ic ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clickable { selectedIcon = ic },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedIcon == ic)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(ic, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                Text("颜色", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    palette.forEach { c ->
                        val cc = runCatching { Color(android.graphics.Color.parseColor(c)) }
                            .getOrDefault(MaterialTheme.colorScheme.primary)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(cc)
                                .then(
                                    if (c == selectedColor)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                                    else Modifier
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (isValid) onConfirm(name, selectedColor, selectedIcon) }, enabled = isValid) {
                Text(if (initial == null) "创建" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
