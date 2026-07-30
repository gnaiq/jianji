package com.example.jianji.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.BuildConfig
import com.example.jianji.data.*
import com.example.jianji.ui.viewmodel.TransactionViewModel
import com.example.jianji.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 设置页各二级页面的分区内容（LazyListScope 扩展 Composable）。
 * 从 SettingsScreen.kt 的 when(destination) 分支机械搬移而来，行为完全不变：
 * 所有状态仍在入口 SettingsScreen remember，此处以参数（值 + 回调）形式接收，未改变状态提升方式。
 */

// ======== 数据管理分区 ========
fun LazyListScope.dataManagementSection(
    scope: CoroutineScope,
    context: Context,
    transactionVM: TransactionViewModel?,
    categories: List<Category>,
    accounts: List<Account>,
    excelExportManager: ExcelExportManager,
    showExportProgress: Boolean,
    onExportProgressChange: (Boolean) -> Unit,
    ensureStoragePermission: (() -> Unit) -> Unit,
    onShowImport: () -> Unit,
    onShowBackupManage: () -> Unit,
    onShowClear: () -> Unit
) {
    item { SectionHeader("数据管理") }

    item {
        SettingsCard(
            icon = Icons.Default.CloudUpload,
            title = "Excel 导出",
            subtitle = "导出交易记录为 .xlsx 格式",
            enabled = !showExportProgress,
            onClick = {
                if (showExportProgress) return@SettingsCard
                onExportProgressChange(true)
                scope.launch {
                    try {
                        val all = transactionVM?.getAllTransactionsSnapshot() ?: emptyList()
                        if (all.isEmpty()) {
                            Toast.makeText(context, "暂无数据可导出", Toast.LENGTH_SHORT).show()
                        } else {
                            val result = excelExportManager.exportToExcel(all, categories, accounts)
                            excelExportManager.shareFile(result.file)
                            Toast.makeText(context, "导出成功(${result.recordCount}条): ${result.file.name}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        onExportProgressChange(false)
                    }
                }
            }
        )
    }

    item {
        SettingsCard(
            icon = Icons.Default.Backup,
            title = "备份数据库",
            subtitle = "导出完整数据(JSON)到下载目录",
            onClick = {
                ensureStoragePermission {
                    scope.launch {
                        try {
                            val db = JianjiDatabase.getDatabase(context.applicationContext)
                            val hasData = db.transactionDao().getAllSnapshot().isNotEmpty()
                                || db.categoryDao().getAllCategories().first().isNotEmpty()
                                || db.accountDao().getAll().isNotEmpty()
                            if (!hasData) {
                                Toast.makeText(context, "暂无数据可备份", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val json = DataImportManager().generateExportJson(db)
                            val fileName = "简记备份_${LocalDate.now()}.json"
                            val savedName = BackupStorage.save(context, fileName, "application/json", json)
                            Toast.makeText(context, "备份成功: $savedName", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    item {
        Text(
            "备份为明文 JSON 保存于公共“下载”目录，包含全部账目数据，请注意保管",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    item {
        SettingsCard(
            icon = Icons.Default.CloudDownload,
            title = "恢复备份",
            subtitle = "从 JSON 备份文件恢复数据",
            onClick = onShowImport
        )
    }

    // === 自动备份 ===
    item {
        var autoBackupDays by remember { mutableIntStateOf(BackupScheduler.getIntervalDays(context)) }
        val autoBackupEnabled = autoBackupDays > 0
        var immediateBackup by remember { mutableStateOf(BackupScheduler.isImmediateBackupEnabled(context)) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("自动备份", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (autoBackupEnabled) "已启用：每${
                                when (autoBackupDays) { 1 -> "天"; 7 -> "周"; 30 -> "月"; else -> "$autoBackupDays 天" }
                            }自动备份一次" else "未启用（数据变更时仍会即时备份）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = autoBackupEnabled, onCheckedChange = { on ->
                        autoBackupDays = if (on) BackupScheduler.DEFAULT_DAYS else 0
                        BackupScheduler.saveIntervalDays(context, autoBackupDays)
                    })
                }
                if (autoBackupEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "每天", 7 to "每周", 30 to "每月").forEach { (days, label) ->
                            FilterChip(
                                selected = autoBackupDays == days,
                                onClick = {
                                    autoBackupDays = days
                                    BackupScheduler.saveIntervalDays(context, days)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("数据变更即时备份", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (immediateBackup) "已开启：数据变更时自动备份" else "已关闭：数据变更不再自动备份",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = immediateBackup, onCheckedChange = { on ->
                        immediateBackup = on
                        BackupScheduler.setImmediateBackupEnabled(context, on)
                    })
                }
            }
        }
    }

    item {
        SettingsCard(
            icon = Icons.Default.Backup,
            title = "CSV 备份",
            subtitle = "导出 CSV 格式到下载目录",
            onClick = {
                ensureStoragePermission {
                    scope.launch {
                        try {
                            val all = transactionVM?.getAllTransactionsSnapshot() ?: emptyList()
                            if (all.isEmpty()) {
                                Toast.makeText(context, "暂无数据可备份", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val csv = buildString {
                            appendLine("ID,日期,类型,分类ID,金额,描述")
                            all.sortedByDescending { it.date }.forEach { tx ->
                                appendLine("${tx.id},${tx.date},${tx.type},${tx.categoryId},${tx.amountCents / 100.0},${tx.description}")
                            }
                        }
                        val fileName = "简记备份_${LocalDate.now()}.csv"
                        val savedName = BackupStorage.save(context, fileName, "text/csv", csv)
                        Toast.makeText(context, "备份成功: $savedName", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            }
        )
    }

    item {
        SettingsCard(
            icon = Icons.Default.DeleteSweep,
            title = "管理备份",
            subtitle = "查看并删除下载目录中的旧备份文件",
            onClick = onShowBackupManage
        )
    }

    item {
        SettingsCard(
            icon = Icons.Default.Delete,
            title = "清除所有数据",
            subtitle = "删除所有交易、分类和设置",
            color = MaterialTheme.colorScheme.error,
            onClick = onShowClear
        )
    }
}

// ======== 关于 & 更新分区 ========
fun LazyListScope.aboutSection(
    scope: CoroutineScope,
    context: Context,
    updateManager: UpdateManager,
    updateStatus: String,
    onUpdateStatusChange: (String) -> Unit,
    downloadProgress: Int,
    onDownloadProgressChange: (Int) -> Unit
) {
    item { SectionHeader("关于 & 更新") }

    item {
        SettingsCard(
            icon = Icons.Default.SystemUpdate,
            title = updateStatus,
            subtitle = "当前版本: ${BuildConfig.VERSION_NAME}",
            onClick = {
                onUpdateStatusChange("检查中...")
                scope.launch {
                    val result = updateManager.checkForUpdate()
                    result.onSuccess { info ->
                        if (info == null) {
                            onUpdateStatusChange("当前已是最新版本")
                        } else {
                            onUpdateStatusChange("发现新版本 v${info.versionName}，开始下载…")
                            onDownloadProgressChange(0)
                            try {
                                updateManager.downloadAndInstall(info.downloadUrl) { p ->
                                    onDownloadProgressChange(p)
                                    onUpdateStatusChange("正在下载更新：$p%")
                                }
                                onUpdateStatusChange("下载完成，正在安装…")
                                onDownloadProgressChange(0)
                            } catch (e: Exception) {
                                onUpdateStatusChange("下载失败: ${e.message ?: "未知错误"}")
                                onDownloadProgressChange(0)
                            }
                        }
                    }.onFailure { e ->
                        // 检查失败，但本机可能已下好安装包 → 直接复用安装
                        if (updateManager.hasLocalApk()) {
                            onUpdateStatusChange("检测到本机已有新版本安装包，正在安装…")
                            updateManager.installLocalApk()
                        } else {
                            onUpdateStatusChange("检查失败: ${e.message}")
                            Toast.makeText(
                                context,
                                "可前往 GitHub 手动下载：${updateManager.releasesUrl()}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        )
    }

    if (downloadProgress in 1..99) {
        item {
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
        item {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = { updateManager.cancelDownload() }) { Text("取消下载") }
            }
        }
    }

    item {
        SettingsCard(
            icon = Icons.Default.Info,
            title = "关于简记",
            subtitle = "v${BuildConfig.VERSION_NAME} | 记录每一笔 · 让生活更有数",
            onClick = {}
        )
    }

    item { Spacer(Modifier.height(80.dp)) }
}
