package com.example.jianji.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.Tag
import com.example.jianji.ui.components.TagFormDialog
import com.example.jianji.ui.viewmodel.TagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    tagVM: TagViewModel,
    onBack: () -> Unit = {}
) {
    val tags by tagVM.tags.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                Text("标签管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { editingTag = null; showForm = true }) {
                Icon(Icons.Default.Add, "新建标签")
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }

        Spacer(Modifier.height(8.dp))
        if (tags.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有标签，点右上角新建一个。", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags) { tag ->
                    TagItem(
                        tag = tag,
                        onEdit = { editingTag = tag; showForm = true },
                        onDelete = { tagVM.deleteTag(tag) }
                    )
                }
            }
        }
    }

    if (showForm) {
        TagFormDialog(
            initial = editingTag,
            onConfirm = { name, color, icon ->
                if (editingTag != null) {
                    tagVM.updateTag(editingTag!!.copy(name = name, color = color, icon = icon))
                } else {
                    tagVM.addTag(name, color, icon)
                }
                showForm = false
                editingTag = null
            },
            onDismiss = { showForm = false; editingTag = null }
        )
    }
}

@Composable
private fun TagItem(
    tag: Tag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = runCatching { Color(android.graphics.Color.parseColor(tag.color)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(tag.icon, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
            Text(tag.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
        }
    }
}
