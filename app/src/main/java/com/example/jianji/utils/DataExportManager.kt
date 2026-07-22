package com.example.jianji.utils

import android.content.Context
import android.os.Environment
import com.example.jianji.data.Category
import com.example.jianji.data.Transaction
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateFormatShort = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 导出为 CSV 格式
     */
    fun exportToCSV(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        fileName: String = "jianji_export_${System.currentTimeMillis()}.csv"
    ): Result<File> {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)
            FileWriter(file).use { fileWriter ->
                CSVPrinter(fileWriter, CSVFormat.DEFAULT.withHeader(
                    "ID", "分类", "金额", "类型", "描述", "日期", "创建时间"
                )).use { csvPrinter ->
                    transactions.forEach { transaction ->
                        val category = categories[transaction.categoryId]?.name ?: "未知"
                        csvPrinter.printRecord(
                            transaction.id,
                            category,
                            transaction.amount,
                            transaction.type.name,
                            transaction.description,
                            dateFormatShort.format(Date.from(transaction.date.atZone(java.time.ZoneId.systemDefault()).toInstant())),
                            dateFormat.format(Date.from(transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant()))
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
     * 导出为 Excel 格式
     */
    fun exportToExcel(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
        fileName: String = "jianji_export_${System.currentTimeMillis()}.xlsx"
    ): Result<File> {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("记账数据")

                // 创建标题行
                val headerRow = sheet.createRow(0)
                val headers = listOf("ID", "分类", "金额", "类型", "描述", "日期", "创建时间")
                val headerStyle = createHeaderStyle(workbook)

                headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = headerStyle
                }

                // 填充数据行
                transactions.forEachIndexed { index, transaction ->
                    val row = sheet.createRow(index + 1)
                    val category = categories[transaction.categoryId]?.name ?: "未知"

                    row.createCell(0).setCellValue(transaction.id.toDouble())
                    row.createCell(1).setCellValue(category)
                    row.createCell(2).setCellValue(transaction.amount)
                    row.createCell(3).setCellValue(transaction.type.name)
                    row.createCell(4).setCellValue(transaction.description)
                    row.createCell(5).setCellValue(dateFormatShort.format(Date.from(transaction.date.atZone(java.time.ZoneId.systemDefault()).toInstant())))
                    row.createCell(6).setCellValue(dateFormat.format(Date.from(transaction.createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant())))
                }

                // 自动调整列宽
                for (i in 0 until headers.size) {
                    sheet.autoSizeColumn(i)
                }

                workbook.write(file.outputStream())
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

    private fun createHeaderStyle(workbook: XSSFWorkbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont()
        font.bold = true
        font.fontHeightInPoints = 12
        style.setFont(font)
        style.fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.index
        style.fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        return style
    }
}
