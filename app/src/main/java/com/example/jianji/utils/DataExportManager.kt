package com.example.jianji.utils

import android.content.Context
import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataExportManager(private val context: Context) {

    private val dateFormat = ThreadLocal<SimpleDateFormat>().apply {
        set(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()))
    }
    private val dateFormatShort = ThreadLocal<SimpleDateFormat>().apply {
        set(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()))
    }

    /**
     * 导出为 CSV 格式
     */
    fun exportToCSV(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        fileName: String = "jianji_export_${System.currentTimeMillis()}.csv"
    ): Result<File> {
        return try {
            val exportDir = File(context.filesDir, "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val file = File(exportDir, fileName)
            FileWriter(file).use { fileWriter ->
                CSVPrinter(fileWriter, CSVFormat.Builder.create().setHeader(
                    "ID", "分类", "金额", "类型", "描述", "日期", "创建时间"
                ).build()).use { csvPrinter ->
                    transactions.forEach { transaction ->
                        val category = categories[transaction.categoryId]?.name ?: "未知"
                        csvPrinter.printRecord(
                            transaction.id,
                            category,
                            transaction.amount,
                            transaction.type.name,
                            transaction.description,
                            dateFormatShort.get()!!.format(Date.from(transaction.date.atZone(java.time.ZoneId.systemDefault()).toInstant())),
                            dateFormat.get()!!.format(Date.from(transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant()))
                        )
                    }
                }
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建备份文件（数据库备份）
     */
    fun createBackup(sourceDbPath: String, fileName: String = "jianji_backup_${System.currentTimeMillis()}.db"): Result<File> {
        return try {
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val sourceFile = File(sourceDbPath)
            val backupFile = File(backupDir, fileName)

            sourceFile.inputStream().use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 恢复备份文件
     */
    fun restoreBackup(backupFile: File, targetDbPath: String): Result<Unit> {
        return try {
            val targetFile = File(targetDbPath)
            backupFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取所有备份文件
     */
    fun getBackupFiles(): List<File> {
        val backupDir = File(context.filesDir, "backups")
        return if (backupDir.exists()) {
            backupDir.listFiles()?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 删除备份文件
     */
    fun deleteBackup(backupFile: File): Boolean {
        return backupFile.delete()
    }
}
