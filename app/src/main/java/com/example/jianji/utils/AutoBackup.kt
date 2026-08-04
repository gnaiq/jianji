package com.example.jianji.utils

import android.content.Context
import com.example.jianji.data.JianjiDatabase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * 统一的数据库自动备份逻辑：读取全部表（含回收站）生成 JSON 并写入自动备份文件。
 * 可被 ViewModel（数据变更时）与定时 Receiver 复用，避免逻辑分散。
 */
object AutoBackup {
    /**
     * 修复 P6-1：若设置了备份口令则加密，否则原样返回（明文向下兼容）。
     * 加密失败不阻断备份主流程，仅记日志退回明文。
     */
    private fun encryptIfNeeded(context: Context, plainJson: String): String {
        val pass = AppPrefs.getBackupPassphrase(context)
        if (pass.isBlank()) return plainJson
        return runCatching { BackupCrypto.encrypt(plainJson, pass) }
            .onFailure { Timber.w(it, "备份加密失败，退化为明文") }
            .getOrDefault(plainJson)
    }
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = JianjiDatabase.getDatabase(context.applicationContext)
            val hasData = db.transactionDao().getAllIncludingDeletedSnapshot().isNotEmpty()
                || db.categoryDao().getAllCategories().first().isNotEmpty()
                || db.accountDao().getAll().isNotEmpty()
            if (!hasData) return@withContext false
            val json = DataImportManager().generateExportJson(db)
            // 修复 P6-1：若用户设置了备份口令，则加密落盘；否则保持明文（向下兼容旧备份）
            val content = encryptIfNeeded(context.applicationContext, json)
            BackupStorage.saveAutoBackup(context.applicationContext, content)
            BackupScheduler.setLastBackupAt(context.applicationContext, System.currentTimeMillis())
            true
        } catch (e: Exception) {
            Timber.w(e, "自动备份失败")
            false
        }
    }

    /**
     * 破坏性操作（清除数据 / 恢复备份）前的「操作前快照」：
     * 落一份带时间戳的备份文件，命名前缀「简记备份_操作前_」不参与自动备份轮转
     * （saveAutoBackup 只轮转 AUTO_PREFIX=「简记备份_自动_」开头文件），
     * 给误操作留后悔药。失败仅记录日志，不阻断主流程。
     */
    suspend fun snapshotBeforeDestructive(context: Context, label: String): Unit = withContext(Dispatchers.IO) {
        try {
            val db = JianjiDatabase.getDatabase(context.applicationContext)
            val json = DataImportManager().generateExportJson(db)
            val content = encryptIfNeeded(context.applicationContext, json)
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            BackupStorage.save(context.applicationContext, "简记备份_操作前_${label}_$ts.json", "application/json", content)
        } catch (e: Exception) {
            Timber.w(e, "操作前快照失败")
        }
    }
}
