package com.example.jianji.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.jianji.data.JianjiDatabase
import com.example.jianji.data.TransactionType
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class JianjiWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = JianjiDatabase.getDatabase(context)
        val now = YearMonth.now()
        val start = now.atDay(1).atStartOfDay()
        val end = now.plusMonths(1).atDay(1).atStartOfDay()
        val todayStart = LocalDate.now().atStartOfDay()
        val tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay()

        val monthExpense = db.transactionDao().getSumByType(TransactionType.EXPENSE, start, end) ?: 0.0
        val monthIncome = db.transactionDao().getSumByType(TransactionType.INCOME, start, end) ?: 0.0
        val todayExpense = db.transactionDao().getSumByType(TransactionType.EXPENSE, todayStart, tomorrowStart) ?: 0.0

        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0; maximumFractionDigits = 0
        }

        provideContent {
            Column {
                Text(
                    text = "简记",
                    style = TextStyle(color = ColorProvider(0xff6200ee.toInt()))
                )
                Row {
                    Column {
                        Text("月支出", style = TextStyle(color = ColorProvider(0xff999999.toInt())))
                        Text("¥${nf.format(monthExpense)}",
                            style = TextStyle(color = ColorProvider(0xffff0000.toInt())))
                    }
                    Column {
                        Text("月收入", style = TextStyle(color = ColorProvider(0xff999999.toInt())))
                        Text("¥${nf.format(monthIncome)}",
                            style = TextStyle(color = ColorProvider(0xff4caf50.toInt())))
                    }
                    Column {
                        Text("今日支出", style = TextStyle(color = ColorProvider(0xff999999.toInt())))
                        Text("¥${nf.format(todayExpense)}",
                            style = TextStyle(color = ColorProvider(0xffff0000.toInt())))
                    }
                }
            }
        }
    }
}
