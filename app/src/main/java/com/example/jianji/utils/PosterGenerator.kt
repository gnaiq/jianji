package com.example.jianji.utils

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.jianji.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

class PosterGenerator(private val context: Context) {

    data class AnnualStats(
        val year: Int,
        val totalIncome: Double,
        val totalExpense: Double,
        val balance: Double,
        val transactionCount: Int,
        val topExpenseCategory: Pair<String, Double>,
        val topIncomeCategory: Pair<String, Double>,
        val monthlyBreakdown: List<Pair<Int, Double>>, // month -> expense
        val maxDailyExpense: Pair<String, Double>,
        val averageDailyExpense: Double
    )

    suspend fun generatePoster(
        transactions: List<Transaction>,
        categories: List<Category>,
        year: Int = LocalDate.now().year
    ): File = withContext(Dispatchers.IO) {
        val stats = computeStats(transactions, categories, year)
        drawPoster(stats)
    }

    private fun computeStats(
        transactions: List<Transaction>,
        categories: List<Category>,
        year: Int
    ): AnnualStats {
        val yearTxs = transactions.filter { it.date.year == year }
        val income = yearTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = yearTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val catMap = categories.associateBy { it.id }

        val expenseByCat = yearTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
        val incomeByCat = yearTxs.filter { it.type == TransactionType.INCOME }
            .groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val topExpense = expenseByCat.maxByOrNull { it.value }
            ?.let { (catMap[it.key]?.name ?: "未知") to it.value } ?: ("无" to 0.0)
        val topIncome = incomeByCat.maxByOrNull { it.value }
            ?.let { (catMap[it.key]?.name ?: "未知") to it.value } ?: ("无" to 0.0)

        val monthlyBreakdown = (1..12).map { month ->
            month to yearTxs.filter { it.date.monthValue == month && it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }
        }

        val dailyMax = yearTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.date.toLocalDate() }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .maxByOrNull { it.value }
        val maxDaily = dailyMax?.let { it.key.format(DateTimeFormatter.ISO_LOCAL_DATE) to it.value } ?: ("无" to 0.0)

        val daysWithTx = yearTxs.map { it.date.toLocalDate() }.distinct().size
        val avgDaily = if (daysWithTx > 0) expense / daysWithTx else 0.0

        return AnnualStats(
            year = year,
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
            transactionCount = yearTxs.size,
            topExpenseCategory = topExpense,
            topIncomeCategory = topIncome,
            monthlyBreakdown = monthlyBreakdown,
            maxDailyExpense = maxDaily,
            averageDailyExpense = avgDaily
        )
    }

    private fun drawPoster(stats: AnnualStats): File {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE; textSize = 72f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#a0a0c0"); textSize = 36f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val amountPaint = Paint().apply {
            color = Color.WHITE; textSize = 64f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.parseColor("#8080a0"); textSize = 28f
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val statValuePaint = Paint().apply {
            color = Color.WHITE; textSize = 32f; isFakeBoldText = true
            textAlign = Paint.Align.LEFT; isAntiAlias = true
        }
        val statLabelPaint = Paint().apply {
            color = Color.parseColor("#8080a0"); textSize = 24f
            textAlign = Paint.Align.LEFT; isAntiAlias = true
        }
        val barPaint = Paint().apply { isAntiAlias = true }
        val greenPaint = Paint().apply {
            color = Color.parseColor("#00e676"); textSize = 48f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val redPaint = Paint().apply {
            color = Color.parseColor("#ff5252"); textSize = 48f; isFakeBoldText = true
            textAlign = Paint.Align.CENTER; isAntiAlias = true
        }

        var y = 200f

        // Title
        canvas.drawText("${stats.year} 年度账单", width / 2f, y, titlePaint)
        y += 60f
        canvas.drawText("简记 · Jianji", width / 2f, y, subtitlePaint)
        y += 120f

        // Balance card
        val cardLeft = 60f
        val cardRight = width - 60f
        val cardPaint = Paint().apply {
            color = Color.parseColor("#1e3a5f")
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardLeft, y, cardRight, y + 280f, 24f, 24f, cardPaint)

        y += 60f
        canvas.drawText(if (stats.balance >= 0) "净结余" else "净超支", width / 2f, y, labelPaint)
        y += 70f
        val balancePaint = if (stats.balance >= 0) greenPaint else redPaint
        canvas.drawText(String.format("¥%,.2f", kotlin.math.abs(stats.balance)), width / 2f, y, balancePaint)
        y += 70f
        val df = java.text.DecimalFormat("#,##0.00")

        // Income / Expense / Count row
        val colWidth = (cardRight - cardLeft) / 3
        val rowY = y
        canvas.drawText("收入", cardLeft + colWidth * 0.5f, rowY, labelPaint)
        canvas.drawText("支出", cardLeft + colWidth * 1.5f, rowY, labelPaint)
        canvas.drawText("笔数", cardLeft + colWidth * 2.5f, rowY, labelPaint)
        canvas.drawText("¥${df.format(stats.totalIncome)}", cardLeft + colWidth * 0.5f, rowY + 50f, statValuePaint)
        canvas.drawText("¥${df.format(stats.totalExpense)}", cardLeft + colWidth * 1.5f, rowY + 50f, statValuePaint)
        canvas.drawText("${stats.transactionCount}笔", cardLeft + colWidth * 2.5f, rowY + 50f, statValuePaint)
        y += 150f

        // Monthly chart
        y += 40f
        canvas.drawText("月度支出走势", width / 2f, y, subtitlePaint)
        y += 60f

        val months = listOf("1月","2月","3月","4月","5月","6月","7月","8月","9月","10月","11月","12月")
        val maxExpense = stats.monthlyBreakdown.maxOfOrNull { it.second } ?: 1.0
        val chartLeft = 100f
        val chartRight = width - 100f
        val chartWidth = chartRight - chartLeft
        val barWidth = chartWidth / 12f - 8f
        val chartBottom = y + 200f

        for ((idx, pair) in stats.monthlyBreakdown.withIndex()) {
            val (month, amt) = pair
            val barH = if (maxExpense > 0) (amt / maxExpense * 180f).toFloat() else 0f
            val barX = chartLeft + idx * (chartWidth / 12f)
            val barColor = if (month == LocalDate.now().monthValue) Color.parseColor("#00e676") else Color.parseColor("#4fc3f7")
            // bar
            barPaint.color = barColor
            canvas.drawRoundRect(barX + 2f, chartBottom - barH, barX + barWidth - 2f, chartBottom, 4f, 4f, barPaint)
            // month label
            if (idx % 2 == 0) {
                labelPaint.textSize = 22f
                canvas.drawText(months[idx], barX + barWidth / 2f, chartBottom + 30f, labelPaint)
            }
        }
        y = chartBottom + 60f

        // Highlights
        y += 20f
        canvas.drawText("精彩亮点", width / 2f, y, subtitlePaint)
        y += 60f

        val bulletPaint = Paint().apply {
            color = Color.parseColor("#FFD700"); textSize = 32f; isFakeBoldText = true
            textAlign = Paint.Align.LEFT; isAntiAlias = true
        }
        val highlights = listOf(
            "最大支出分类: ${stats.topExpenseCategory.first}  ¥${df.format(stats.topExpenseCategory.second)}",
            "最大收入分类: ${stats.topIncomeCategory.first}  ¥${df.format(stats.topIncomeCategory.second)}",
            "最高单日支出: ${stats.maxDailyExpense.first}  ¥${df.format(stats.maxDailyExpense.second)}",
            "日均支出: ¥${df.format(stats.averageDailyExpense)}"
        )
        for (h in highlights) {
            canvas.drawText(h, cardLeft + 20f, y, statValuePaint)
            y += 55f
        }

        // Footer
        y = (height - 80f).toFloat()
        canvas.drawText("简记 Jianji", width / 2f, y, labelPaint)
        y += 40f
        labelPaint.textSize = 20f
        canvas.drawText("记录每一笔 · 让生活更有数", width / 2f, y, labelPaint)

        // Save to file
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val posterDir = File(baseDir, "简记海报")
        posterDir.mkdirs()
        val file = File(posterDir, "简记_${stats.year}年度账单.png")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }
        bitmap.recycle()
        return file
    }

    fun sharePoster(file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享年度账单"))
    }
}
