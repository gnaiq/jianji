package com.example.jianji.ui.viewmodel

import com.example.jianji.data.Transaction
import com.example.jianji.data.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * 验收 B3-4：账户余额以 Long 分累加，无浮点精度丢失。
 */
class AccountBalanceCentsTest {
    private fun tx(
        id: Long, type: TransactionType, cents: Long,
        accountId: Long? = 1L, toAccountId: Long? = null
    ) = Transaction(
        id = id, categoryId = 1, amountCents = cents, type = type,
        date = LocalDateTime.now(), accountId = accountId, toAccountId = toAccountId
    )

    @Test
    fun `普通收支按分累加无误`() {
        val txs = listOf(
            tx(1, TransactionType.INCOME, 100),   // +1.00
            tx(2, TransactionType.EXPENSE, 30),   // -0.30
            tx(3, TransactionType.EXPENSE, 70)    // -0.70
        )
        val balances = computeAccountBalancesCents(txs)
        assertEquals(0L, balances[1]) // 100 - 30 - 70 = 0 分
    }

    @Test
    fun `转账起账户减止账户加`() {
        val txs = listOf(tx(1, TransactionType.TRANSFER, 500, accountId = 1L, toAccountId = 2L))
        val balances = computeAccountBalancesCents(txs)
        assertEquals(-500L, balances[1])
        assertEquals(500L, balances[2])
    }

    @Test
    fun `大额多笔累加不产生浮点误差`() {
        // 0.01 元 * 100 笔支出 = -1.00 元（若用 Double 累加 0.01 会漂移）
        val txs = (1L..100L).map { tx(it, TransactionType.EXPENSE, 1, accountId = 1L) }
        val balances = computeAccountBalancesCents(txs)
        assertEquals(-100L, balances[1]) // 精确 -100 分
    }
}
