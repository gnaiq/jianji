package com.example.jianji.utils

import android.content.Context
import com.example.jianji.data.JianjiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 统一的数据库自动备份逻辑：读取全部 6 张表生成 JSON 并写入自动备份文件。
 * 可被 ViewModel（数据变更时）与定时 Receiver 复用，避免逻辑分散。
 */
object AutoBackup {
    suspend fun run(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = JianjiDatabase.getDatabase(context.applicationContext)
            val hasData = db.transactionDao().getAllSnapshot().isNotEmpty()
                || db.categoryDao().getAllCategories().first().isNotEmpty()
                || db.accountDao().getAll().isNotEmpty()
            if (!hasData) return@withContext false
            val json = DataImportManager().generateExportJson(db)
            BackupStorage.saveAutoBackup(context.applicationContext, json)
            true
        } catch (_: Exception) {
            false
        }
    }
}
