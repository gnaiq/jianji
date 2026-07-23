package com.example.jianji.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.jianji.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 无外部依赖的 XLSX 生成器。
 * XLSX 本质是 ZIP 内若干 XML 文件。
 */
class ExcelExportManager(private val context: Context) {

    data class ExportResult(val file: File, val recordCount: Int)

    suspend fun exportToExcel(
        transactions: List<Transaction>,
        categories: List<Category>,
        accounts: List<Account>
    ): ExportResult = withContext(Dispatchers.IO) {
        val sortedTx = transactions.sortedByDescending { it.date }
        val catMap = categories.associateBy { it.id }
        val accMap = accounts.associateBy { it.id }
        val df = DecimalFormat("#,##0.00")
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val now = LocalDate.now()

        // Escape XML special characters
        fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

        // Collect shared strings and build sheet data
        val sharedStrings = linkedSetOf<String>()
        val sheetRows = StringBuilder()

        // Header
        val headers = listOf("ID", "日期", "类型", "分类", "金额", "描述", "账户")
        fun cell(c: Int, value: String, isNumber: Boolean = false, style: Int = if (c == 0) 1 else 0) {
            val si = if (isNumber) "" else " t=\"s\""
            sheetRows.append("<c r=\"${colLetter(c)}${1}\"$si><v>${if (isNumber) value else sharedStrings.indexOf(value)}</v></c>")
        }

        // Build header row
        sharedStrings.addAll(headers)
        sheetRows.append("<row r=\"1\">")
        headers.forEachIndexed { i, h -> cell(i, h) }
        sheetRows.append("</row>")

        // Data rows
        sortedTx.forEachIndexed { idx, tx ->
            val rowNum = idx + 2
            sheetRows.append("<row r=\"$rowNum\">")
            sharedStrings.add(tx.id.toString())
            sheetRows.append("<c r=\"A$rowNum\" t=\"s\"><v>${sharedStrings.indexOf(tx.id.toString())}</v></c>")

            val dateStr = tx.date.format(dateFmt)
            sharedStrings.add(dateStr)
            sheetRows.append("<c r=\"B$rowNum\" t=\"s\"><v>${sharedStrings.indexOf(dateStr)}</v></c>")

            val typeStr = if (tx.type == TransactionType.EXPENSE) "支出" else "收入"
            sharedStrings.add(typeStr)
            sheetRows.append("<c r=\"C$rowNum\" t=\"s\"><v>${sharedStrings.indexOf(typeStr)}</v></c>")

            val catStr = catMap[tx.categoryId]?.name ?: "未知"
            sharedStrings.add(catStr)
            sheetRows.append("<c r=\"D$rowNum\" t=\"s\"><v>${sharedStrings.indexOf(catStr)}</v></c>")

            sheetRows.append("<c r=\"E$rowNum\"><v>${tx.amount}</v></c>")

            sharedStrings.add(tx.description)
            sheetRows.append("<c r=\"F$rowNum\" t=\"s\"><v>${sharedStrings.indexOf(tx.description)}</v></c>")

            val accStr = tx.accountId?.let { accMap[it]?.name } ?: ""
            sharedStrings.add(accStr)
            sheetRows.append("<c r=\"G$rowNum\" t=\"s\"><v>${sharedStrings.indexOf(accStr)}</v></c>")

            sheetRows.append("</row>")
        }

        // Build shared strings XML
        val sharedStringsXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<sst count=\"${sharedStrings.size}\" uniqueCount=\"${sharedStrings.size}\" xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            sharedStrings.forEach { s -> append("<si><t>${esc(s)}</t></si>") }
            append("</sst>")
        }

        // Build worksheet XML
        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<cols>")
            append("<col min=\"1\" max=\"1\" width=\"10\" bestFit=\"true\"/>")
            append("<col min=\"2\" max=\"2\" width=\"18\" bestFit=\"true\"/>")
            append("<col min=\"3\" max=\"3\" width=\"8\" bestFit=\"true\"/>")
            append("<col min=\"4\" max=\"4\" width=\"14\" bestFit=\"true\"/>")
            append("<col min=\"5\" max=\"5\" width=\"14\" bestFit=\"true\"/>")
            append("<col min=\"6\" max=\"6\" width=\"30\" bestFit=\"true\"/>")
            append("<col min=\"7\" max=\"7\" width=\"14\" bestFit=\"true\"/>")
            append("</cols>")
            append("<sheetData>")
            append(sheetRows.toString())
            append("</sheetData>")
            append("</worksheet>")
        }

        // Build styles XML (simple)
        val stylesXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            append("<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/></numFmts>")
            append("<fonts count=\"2\">")
            append("<font><sz val=\"11\"/><name val=\"Calibri\"/></font>")
            append("<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>")
            append("</fonts>")
            append("<fills count=\"2\">")
            append("<fill><patternFill patternType=\"none\"/></fill>")
            append("<fill><patternFill patternType=\"gray125\"/></fill>")
            append("</fills>")
            append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
            append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
            append("<cellXfs count=\"2\">")
            append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
            append("<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>")
            append("</cellXfs>")
            append("</styleSheet>")
        }

        // Build workbook XML
        val workbookXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            append("<sheets><sheet name=\"交易记录\" sheetId=\"1\" r:id=\"rId1\"/></sheets>")
            append("</workbook>")
        }

        val relsXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>")
            append("</Relationships>")
        }

        val wbRelsXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>")
            append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>")
            append("<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
            append("</Relationships>")
        }

        val contentTypeXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>")
            append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            append("</Types>")
        }

        // Write ZIP (XLSX)
        val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "简记导出")
        exportDir.mkdirs()
        val file = File(exportDir, "简记数据_$now.xlsx")

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypeXml)
            addEntry("_rels/.rels", relsXml)
            addEntry("xl/workbook.xml", workbookXml)
            addEntry("xl/_rels/workbook.xml.rels", wbRelsXml)
            addEntry("xl/worksheets/sheet1.xml", sheetXml)
            addEntry("xl/sharedStrings.xml", sharedStringsXml)
            addEntry("xl/styles.xml", stylesXml)
        }

        ExportResult(file, sortedTx.size)
    }

    private fun colLetter(index: Int): String {
        return ('A' + index).toString()
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 Excel"))
    }
}
