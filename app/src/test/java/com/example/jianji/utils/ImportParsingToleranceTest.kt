package com.example.jianji.utils

import com.example.jianji.data.RecurringFrequency
import com.example.jianji.data.TransactionType
import com.example.jianji.utils.DataImportManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * 导入解析容错的纯 JVM 守护测试（P1-阶段二·项5 配套）。
 *
 * importFromJson 对交易/周期记录采用「逐条 try-catch 跳过 + 计数」策略，其正确性
 * 建立在下述解析行为之上：非法日期 / 非法枚举会抛异常。本测试锁定这些前提，
 * 一旦底层解析语义变化（例如换用宽松解析器）能第一时间暴露，防止跳过逻辑静默失效。
 */
class ImportParsingToleranceTest {

    @Test
    fun validDate_parsesToLocalDateTime() {
        val dt = LocalDateTime.parse("2026-04-15T12:00:00")
        assertEquals(2026, dt.year)
        assertEquals(4, dt.monthValue)
        assertEquals(15, dt.dayOfMonth)
    }

    @Test
    fun invalidDate_throws() {
        assertThrows(DateTimeParseException::class.java) {
            LocalDateTime.parse("not-a-date")
        }
    }

    @Test
    fun validFrequency_parsesToEnum() {
        assertEquals(RecurringFrequency.MONTHLY, RecurringFrequency.valueOf("MONTHLY"))
        assertEquals(RecurringFrequency.WEEKLY, RecurringFrequency.valueOf("WEEKLY"))
    }

    @Test
    fun invalidFrequency_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurringFrequency.valueOf("BADFREQ")
        }
    }

    @Test
    fun transactionType_mappingMatchesImportBranches() {
        // importFromJson 的交易类型分支：INCOME / TRANSFER / EXPENSE 命中，其余抛异常跳过
        assertEquals(TransactionType.INCOME, TransactionType.valueOf("INCOME"))
        assertEquals(TransactionType.TRANSFER, TransactionType.valueOf("TRANSFER"))
        assertEquals(TransactionType.EXPENSE, TransactionType.valueOf("EXPENSE"))
        assertThrows(IllegalArgumentException::class.java) {
            TransactionType.valueOf("WEIRD")
        }
    }

    @Test
    fun amountToCents_roundsHalfUp() {
        // 与 importFromJson 中 Math.round(amount * 100) 一致，防浮点误差（8.20 -> 820）
        assertEquals(820L, Math.round(8.20 * 100))
        assertEquals(1250L, Math.round(12.5 * 100))
        assertTrue(Math.round(0.1 * 100 + 0.2 * 100) == 30L)
    }

    @Test
    fun oldBackupWithAccountBalanceField_parsesWithoutCrash() {
        // DEF-004 回归：AccountImport 已移除 balance 字段，但旧版备份 JSON 仍含 balance。
        // Gson 默认忽略未知字段，应正常解析（不抛异常、不返回 null），防止旧备份恢复失败。
        val legacyJson = """
        {
          "version": 4,
          "accounts": [
            { "id": 1, "name": "现金", "icon": "💵", "isDefault": true, "balance": 1234.56 }
          ],
          "transactions": [],
          "categories": []
        }
        """.trimIndent()
        val data = runBlocking { DataImportManager().parseJson(legacyJson) }
        assertNotNull("旧备份含 balance 字段仍应解析成功", data)
        assertEquals(1, data?.accounts?.size)
        assertEquals("现金", data?.accounts?.first()?.name)
    }
}
