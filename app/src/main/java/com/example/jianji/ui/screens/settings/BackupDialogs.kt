package com.example.jianji.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.jianji.data.*
import com.example.jianji.core.backup.AutoBackup
import com.example.jianji.core.backup.BackupCrypto
import com.example.jianji.core.backup.BackupFileEntry
import com.example.jianji.core.backup.BackupStorage
import com.example.jianji.core.backup.DataImportManager
import kotlinx.coroutines.launch

/**
 * 备份恢复 / 备份管理弹窗。
 * 从 SettingsScreen.kt 纯搬移，逻辑与状态提升方式未改动。
 */

// ======== Import Dialog ========
@Composable
fun ImportDialog(
    ensureStoragePermission: (() -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 执行恢复：清空现有交易并按备份重新写入（替换语义）
    val doImport: () -> Unit = {
        if (jsonText.isNotBlank()) {
            ensureStoragePermission {
                importing = true
                scope.launch {
                    try {
                        // 破坏性操作（覆盖现有全部数据）前先落一份「操作前快照」备份，
                        // 命名前缀「简记备份_操作前_」不参与自动备份轮转，给误操作留后悔药（P1-8）
                        AutoBackup.snapshotBeforeDestructive(context, "恢复备份前")
                        val importer = DataImportManager()
                        val result = importer.importFromJson(
                            jsonText, JianjiDatabase.getDatabase(context.applicationContext), context
                        )
                        importing = false
                        val ok = result.transactionCount > 0 || result.categoryCount > 0
                        if (ok) {
                            val detail = if (result.isFullRestore) "（已恢复账户/预算/周期/模板）"
                                else "（旧格式备份，仅恢复交易+分类）"
                            val parts = mutableListOf<String>()
                            if (result.transactionCount > 0) parts.add("${result.transactionCount} 笔交易")
                            if (result.categoryCount > 0) parts.add("${result.categoryCount} 个分类")
                            val skipNote = if (result.skippedCount > 0) "，跳过 ${result.skippedCount} 笔无效记录" else ""
                            Toast.makeText(context, "恢复成功，导入 ${parts.joinToString("、")}$detail$skipNote", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "未导入数据，请检查文件格式", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        importing = false
                        Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 自动检测共享下载目录中的备份文件（卸载后保留）
    LaunchedEffect(Unit) {
        backups = BackupStorage.list(context)
    }

    // 加密备份的口令解对话框状态
    var decryptPass by remember { mutableStateOf("") }
    var showDecrypt by remember { mutableStateOf(false) }

    fun tryDecrypt(raw: String): Boolean = try {
        jsonText = BackupCrypto.decrypt(raw, decryptPass)
        true
    } catch (e: Exception) {
        Toast.makeText(context, "解密失败：口令错误或文件损坏", Toast.LENGTH_SHORT).show()
        false
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@rememberLauncherForActivityResult
            if (BackupCrypto.isEncrypted(raw)) {
                decryptPass = ""
                showDecrypt = true
                jsonText = raw // 暂存密文，解密成功后再覆盖为明文
            } else {
                jsonText = raw
            }
        } catch (e: Exception) {
            Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDecrypt) {
        AlertDialog(
            onDismissRequest = { showDecrypt = false },
            title = { Text("输入备份口令") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("该备份已加密，请输入加密时设置的口令。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = decryptPass, onValueChange = { decryptPass = it },
                        label = { Text("口令") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (tryDecrypt(jsonText)) showDecrypt = false
                }) { Text("解密") }
            },
            dismissButton = { TextButton(onClick = { showDecrypt = false }) { Text("取消") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (backups.isNotEmpty()) {
                    Text("检测到以下备份（点击选择）", style = MaterialTheme.typography.labelMedium)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(4.dp)
                        ) {
                            items(backups) { entry ->
                                val sizeKb = (entry.size / 1024.0).let { if (it < 1) "<1" else "%.1f".format(it) }
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        try {
                                            jsonText = BackupStorage.read(context, entry.uri)
                                            selectedName = entry.name
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("${sizeKb}KB", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                } else {
                    Text("未检测到备份，可手动选择文件或粘贴 JSON 数据",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { filePicker.launch("application/json") }) {
                    Text("选择备份文件")
                }
                // 大备份 JSON 直接塞进可编辑 TextField 会触发 Compose 布局超大文本导致 ANR（P1-6）：
                // 仅用截断预览展示，完整内容仍保存在 jsonText 中用于导入；
                // 超过 4000 字符改为只读，避免编辑/重排版时再次布局全量文本。
                val jsonPreview = if (jsonText.length > 4000) {
                    jsonText.take(4000) + "\n…(已截断显示，完整内容共 ${jsonText.length} 字符)"
                } else jsonText
                if (selectedName != null) {
                    Text(
                        "已选择备份：$selectedName（${jsonText.length} 字符）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = jsonPreview,
                    onValueChange = { if (jsonText.length <= 4000) jsonText = it },
                    readOnly = jsonText.length > 4000,
                    label = { Text("JSON 数据") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { showRestoreConfirm = true },
                enabled = jsonText.isNotBlank() && !importing
            ) { Text(if (importing) "恢复中..." else "恢复") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复备份") },
            text = { Text("恢复将清空当前所有交易记录并按备份重新写入，且不可撤销。确定要从该备份恢复吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        doImport()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确定恢复") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
fun BackupManagementDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var backups by remember { mutableStateOf<List<BackupFileEntry>>(emptyList()) }
    var toDelete by remember { mutableStateOf<BackupFileEntry?>(null) }
    var showDeleteAll1 by remember { mutableStateOf(false) }
    var showDeleteAll2 by remember { mutableStateOf(false) }
    var verifyText by remember { mutableStateOf("") }

    fun refresh() { backups = BackupStorage.list(context) }
    LaunchedEffect(Unit) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理备份") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下载目录中的备份文件，可删除不再需要的旧备份", style = MaterialTheme.typography.bodyMedium)
                if (backups.isEmpty()) {
                    Text("暂无备份文件", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(4.dp)) {
                        items(backups) { entry ->
                            val sizeKb = (entry.size / 1024.0).let { if (it < 1) "<1" else "%.1f".format(it) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("${sizeKb}KB", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                IconButton(onClick = { toDelete = entry }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.padding(end = 8.dp)) {
                if (backups.isNotEmpty()) {
                    TextButton(
                        onClick = { showDeleteAll1 = true; verifyText = "" },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除全部") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定删除备份「${toDelete!!.name}」？删除后不可恢复。") },
            confirmButton = {
                Button(onClick = {
                    val entry = toDelete!!
                    try {
                        BackupStorage.delete(context, entry.uri)
                        Toast.makeText(context, "已删除: ${entry.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    toDelete = null
                    refresh()
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }

    if (showDeleteAll1) {
        AlertDialog(
            onDismissRequest = { showDeleteAll1 = false },
            title = { Text("删除全部备份") },
            text = { Text("确定要删除全部 ${backups.size} 个备份文件吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAll1 = false
                        showDeleteAll2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll1 = false }) { Text("取消") } }
        )
    }

    if (showDeleteAll2) {
        AlertDialog(
            onDismissRequest = { showDeleteAll2 = false },
            title = { Text("二次确认") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请输入「删除」以确认删除全部备份：")
                    OutlinedTextField(
                        value = verifyText,
                        onValueChange = { verifyText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        BackupStorage.deleteAll(context)
                        Toast.makeText(context, "已删除全部 ${backups.size} 个备份", Toast.LENGTH_SHORT).show()
                        verifyText = ""
                        showDeleteAll2 = false
                        refresh()
                    },
                    enabled = verifyText == "删除",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll2 = false }) { Text("取消") } }
        )
    }
}
