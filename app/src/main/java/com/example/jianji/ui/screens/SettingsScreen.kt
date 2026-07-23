package com.example.jianji.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import com.example.jianji.utils.DataExportManager
import com.example.jianji.utils.UpdateManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    transactions: List<Transaction> = emptyList(),
    categories: List<Category> = emptyList(),
    onDataCleared: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateManager.ReleaseInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var backupFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    // 版本号
    val appInfo = remember {
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            Pair(pi.versionName ?: "?", pi.versionCode)
        } catch (_: Exception) {
            Pair("?", 0)
        }
    }

    val exportManager = remember { DataExportManager(context) }
    val updateManager = remember { UpdateManager(context) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除所有数据") },
            text = { Text("此操作将删除所有账目记录和自定义分类。默认分类会重置。此操作不可撤销，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onDataCleared?.invoke()
                    Toast.makeText(context, "数据已清除", Toast.LENGTH_SHORT).show()
                }) {
                    Text("确认清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRestoreDialog) {
        val files = backupFiles
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("选择备份文件恢复") },
            text = {
                if (files.isEmpty()) {
                    Text("暂无备份文件")
                } else {
                    Column {
                        Text("恢复备份将覆盖当前数据，确定继续？", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        files.sortedByDescending { it.lastModified() }.forEach { file ->
                            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(file.lastModified()))
                            TextButton(
                                onClick = {
                                    showRestoreDialog = false
                                    scope.launch {
                                        val dbPath = context.getDatabasePath("jianji_database").absolutePath
                                        exportManager.restoreBackup(file, dbPath).fold(
                                            onSuccess = {
                                                Toast.makeText(context, "恢复成功，请重启应用", Toast.LENGTH_LONG).show()
                                            },
                                            onFailure = { e ->
                                                Toast.makeText(context, "恢复失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(file.name, fontWeight = FontWeight.Medium)
                                    Text(date, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ═══ 版本更新 ═══
        Text("版本更新", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("当前版本", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "v${appInfo.first} (build ${appInfo.second})",
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                    }
                    Button(
                        onClick = {
                            isChecking = true
                            updateError = null
                            scope.launch {
                                updateManager.checkForUpdate().fold(
                                    onSuccess = { info ->
                                        if (info == null) {
                                            Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                                        } else {
                                            updateInfo = info
                                        }
                                    },
                                    onFailure = { e ->
                                        updateError = "检查失败: ${e.message}"
                                    }
                                )
                                isChecking = false
                            }
                        },
                        enabled = !isChecking
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isChecking) "检查中" else "检查更新")
                    }
                }

                // 更新详情
                updateInfo?.let { info ->
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("发现新版本 v${info.versionName}", fontWeight = FontWeight.Bold)
                    if (info.body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            info.body.take(200),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val id = updateManager.downloadAndInstall(info.downloadUrl)
                            Toast.makeText(context, "开始下载更新...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("下载并安装 (%.1f MB)".format(info.apkSize / 1024.0 / 1024.0))
                    }
                }

                updateError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ═══ 数据管理 ═══
        Text("数据管理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                // 导出 CSV
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "导出 CSV",
                    subtitle = "将所有账目导出为 CSV 文件",
                    trailing = if (isExporting) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else null,
                    onClick = {
                        if (transactions.isEmpty()) {
                            Toast.makeText(context, "暂无数据可导出", Toast.LENGTH_SHORT).show()
                            return@SettingsItem
                        }
                        isExporting = true
                        scope.launch {
                            val catMap = categories.associate { it.id to it }
                            exportManager.exportToCSV(transactions, catMap).fold(
                                onSuccess = { file ->
                                    // Copy to Downloads for user access
                                    try {
                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val destFile = File(downloadsDir, file.name)
                                        file.inputStream().use { src ->
                                            FileOutputStream(destFile).use { dst -> src.copyTo(dst) }
                                        }
                                        Toast.makeText(context, "已导出到下载目录: ${destFile.name}", Toast.LENGTH_LONG).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "已导出: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                onFailure = { e ->
                                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            isExporting = false
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 数据库备份
                SettingsItem(
                    icon = Icons.Default.Backup,
                    title = "数据库备份",
                    subtitle = "创建当前数据库的完整备份",
                    trailing = if (isBackingUp) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else null,
                    onClick = {
                        if (transactions.isEmpty()) {
                            Toast.makeText(context, "暂无数据，跳过备份", Toast.LENGTH_SHORT).show()
                            return@SettingsItem
                        }
                        isBackingUp = true
                        scope.launch {
                            val dbPath = context.getDatabasePath("jianji_database").absolutePath
                            exportManager.createBackup(dbPath).fold(
                                onSuccess = { file ->
                                    Toast.makeText(context, "备份完成: ${file.name}", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { e ->
                                    Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            isBackingUp = false
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // 恢复备份
                SettingsItem(
                    icon = Icons.Default.Restore,
                    title = "恢复备份",
                    subtitle = "从备份文件恢复数据",
                    onClick = {
                        backupFiles = exportManager.getBackupFiles()
                        showRestoreDialog = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ═══ 关于 ═══
        Text("关于", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "简记 (Jianji)",
                    subtitle = "简洁高效的记账工具 · 数据完全本地存储",
                    onClick = {}
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "GitHub 项目主页",
                    subtitle = "gnaiq/jianji",
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/gnaiq/jianji")
                        )
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ═══ 危险区域 ═══
        Text("危险区域", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
        ) {
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "清除所有数据",
                subtitle = "删除所有账目和分类，恢复初始状态",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showClearDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (titleColor == MaterialTheme.colorScheme.error) titleColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, color = titleColor)
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}
